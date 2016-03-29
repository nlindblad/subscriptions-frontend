package services

import com.gu.config.DiscountRatePlanIds
import com.gu.identity.play.AuthenticatedIdUser
import com.gu.memsub.services.PromoService
import com.gu.memsub.services.api.CatalogService
import com.gu.salesforce.ContactId
import com.gu.stripe.Stripe
import com.gu.zuora.api.ZuoraService
import com.gu.zuora.soap.models.Commands.{Subscribe, RatePlan}
import com.gu.zuora.soap.models.Commands.Lenses._
import com.gu.zuora.soap.models.Results.SubscribeResult
import com.gu.zuora.soap.models.errors._
import com.typesafe.scalalogging.LazyLogging
import model._
import model.error.CheckoutService._
import model.error.IdentityService._
import model.error.SubsError
import touchpoint.ZuoraProperties
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.{NonEmptyList, EitherT, \/, -\/, \/-}
import scalaz.std.scalaFuture._

class CheckoutService(identityService: IdentityService,
                      salesforceService: SalesforceService,
                      paymentService: PaymentService,
                      catalogService: CatalogService,
                      zuoraService: ZuoraService,
                      exactTargetService: ExactTargetService,
                      zuoraProperties: ZuoraProperties,
                      promoService: PromoService,
                      promoPlans: DiscountRatePlanIds) extends LazyLogging {

  def processSubscription(subscriptionData: SubscriptionData,
                          authenticatedUserOpt: Option[AuthenticatedIdUser],
                          requestData: SubscriptionRequestData
                         ): Future[NonEmptyList[SubsError] \/ CheckoutSuccess] = {

    val personalData = subscriptionData.personalData
    val plan = RatePlan(subscriptionData.productRatePlanId.get, None)

    authenticatedUserOpt match {
      case Some(authenticatedIdUser) => userBecomesSubscriber(
        authenticatedUserOpt, personalData, RegisteredUser(authenticatedIdUser.user), requestData, plan, subscriptionData)

      case _ =>
        logger.info(s"User does not have an Identity account. Creating a guest account")

        def success(personalData: PersonalData, requestData: SubscriptionRequestData,
                    plan: RatePlan, subscriptionData: SubscriptionData)(identity: IdentitySuccess) =
          userBecomesSubscriber(None, personalData, identity.userData, requestData, plan, subscriptionData)

        def failure(errSeq: NonEmptyList[SubsError]) = Future.successful(\/.left(errSeq.<::(CheckoutIdentityFailure(
          "User could not subscribe during checkout because Identity guest account could not be created",
          subscriptionData.toString,
          None))))

        identityService.registerGuest(personalData).flatMap{_.fold(
          failure, success(personalData, requestData, plan, subscriptionData))}
    }
  }

  private def userBecomesSubscriber(
       authenticatedUserOpt: Option[AuthenticatedIdUser],
       personalData: PersonalData,
       userData: UserIdData,
       requestData: SubscriptionRequestData,
       plan: RatePlan,
       subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ CheckoutSuccess] = {

    val identityUpdate = authenticatedUserOpt.map(
      updateAuthenticatedUserDetails(_, personalData, subscriptionData)
    ).getOrElse(Future.successful(\/.right(Unit))) // just say we succeeded if we did no update

    (for {
      memberId <- EitherT(createOrUpdateUser(personalData, userData, subscriptionData))
      subscribe <- EitherT(createSubscribeRequest(personalData, requestData, plan, userData, memberId, subscriptionData))
      withPromo = promoService.applyPromotion(subscribe, subscriptionData.suppliedPromoCode, Some(personalData.country))
      withGrace = withPromo.copy(paymentDelay = withPromo.paymentDelay.map(_.plus(zuoraProperties.gracePeriodInDays)))
      result <- EitherT(createSubscription(withGrace, userData, subscriptionData))
      _ <- EitherT(sendETDataExtensionRow(result, subscriptionData))
      _ <- EitherT(identityUpdate)
    } yield {
      CheckoutSuccess(memberId, userData, result, subscribe.promoCode)
    }).run
  }

  private def updateAuthenticatedUserDetails(
      authenticatedUser: AuthenticatedIdUser,
      personalData: PersonalData,
      subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ IdentitySuccess] =

    identityService.updateUserDetails(personalData)(authenticatedUser).map {
      case \/-(IdentitySuccess(user)) => \/.right(IdentitySuccess(user))

      case -\/(errSeq) =>
        \/.left(errSeq.<::(CheckoutIdentityFailure(
          "Registered user could not subscribe during checkout because Identity details could not be updated",
          subscriptionData.toString,
          None)))
    }

  private def sendETDataExtensionRow(
      subscribeResult: SubscribeResult,
      subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ Unit] =

    (for {
      a <- exactTargetService.sendETDataExtensionRow(subscribeResult, subscriptionData, zuoraProperties.gracePeriodInDays)
    } yield {
      \/.right(())
    }).recover {
      case e => \/.left(NonEmptyList(CheckoutExactTargetFailure(
        s"User could not subscribe during checkout because ExactTarget failed to send the subscription email",
        subscriptionData.toString,
        None)))
    }

  private def createOrUpdateUser(
      personalData: PersonalData,
      userData: UserIdData,
      subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ ContactId] = {

    (for {
      memberId <- salesforceService.createOrUpdateUser(personalData, userData.id)
    } yield {
      \/.right(memberId)
    }).recover {
      case e => \/.left(NonEmptyList(CheckoutSalesforceFailure(
        userData.id.id,
        s"User ${userData.id.id} could not subscribe during checkout because his details could not be updated in Salesforce",
        subscriptionData.toString,
        None)))
    }
  }

  private def createSubscribeRequest(
     personalData: PersonalData, requestData: SubscriptionRequestData,
     plan: RatePlan,
     userData: UserIdData,
     memberId: ContactId,
     subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ Subscribe] = {

    val payment = subscriptionData.paymentData match {
      case paymentData@DirectDebitData(_, _, _) =>
        paymentService.makeDirectDebitPayment(paymentData, personalData, memberId)
      case paymentData@CreditCardData(_) =>
        val plan = catalogService.digipackCatalog.unsafeFind(subscriptionData.productRatePlanId)
        paymentService.makeCreditCardPayment(paymentData, personalData, userData, memberId, plan)
    }

    payment.makePaymentMethod.map { method =>
      \/.right(Subscribe(
        account = payment.makeAccount, Some(method),
        ratePlans = NonEmptyList(plan),
        name = personalData,
        address = personalData.address,
        paymentDelay = Some(zuoraProperties.paymentDelayInDays),
        ipAddress = requestData.ipAddress.map(_.getHostAddress)
      ))
    }.recover {
      case e: Stripe.Error => \/.left(NonEmptyList(CheckoutStripeError(
        userData.id.id,
        e,
        s"User ${userData.id.id} could not subscribe during checkout due to Stripe API error",
        subscriptionData.toString,
        None)))

      case e => \/.left(NonEmptyList(CheckoutGenericFailure(
        userData.id.id,
        s"User ${userData.id.id} could not subscribe during checkout",
        subscriptionData.toString,
        None)))
    }
  }

  private def createSubscription(
      subscribe: Subscribe,
      userData: UserIdData,
      subscriptionData: SubscriptionData): Future[NonEmptyList[SubsError] \/ SubscribeResult] = {

    zuoraService.createSubscription(subscribe).map(\/.right).recover {
      case e: PaymentGatewayError => \/.left(NonEmptyList(CheckoutZuoraPaymentGatewayError(
        userData.id.id,
        e,
        s"User ${userData.id.id} could not subscribe during checkout due to Zuora Payment Gateway Error",
        subscriptionData.toString,
        None)))

      case e => \/.left(NonEmptyList(CheckoutGenericFailure(
        userData.id.id,
        s"User ${userData.id.id} could not subscribe during checkout",
        subscriptionData.toString,
        None)))
    }
  }
}
