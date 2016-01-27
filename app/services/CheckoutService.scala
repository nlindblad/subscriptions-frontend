package services

import com.gu.identity.play.AuthenticatedIdUser
import com.gu.memsub.Subscription.AccountId
import com.gu.memsub.services.PromoService
import com.gu.memsub.services.api.{CatalogService, SubscriptionService}
import com.gu.salesforce.ContactId
import com.gu.zuora.api.ZuoraService
import com.gu.zuora.soap.models.Results.SubscribeResult
import com.typesafe.scalalogging.LazyLogging
import model._
import touchpoint.ZuoraProperties
import configuration.Config.productFamily

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CheckoutService {
  case class CheckoutResult(salesforceMember: ContactId, userIdData: UserIdData, subscribeResult: SubscribeResult)
}

class CheckoutService(identityService: IdentityService,
                      subscriptionService: SubscriptionService,
                      salesforceService: SalesforceService,
                      paymentService: PaymentService,
                      catalogService: CatalogService,
                      zuoraService: ZuoraService,
                      exactTargetService: ExactTargetService,
                      zuoraProperties: ZuoraProperties,
                      promoService: PromoService) extends LazyLogging {

  import CheckoutService.CheckoutResult

  def processSubscription(subscriptionData: SubscriptionData,
                          authenticatedUserOpt: Option[AuthenticatedIdUser],
                          requestData: SubscriptionRequestData
                         ): Future[CheckoutResult] = {

    val personalData = subscriptionData.personalData

    def updateAuthenticatedUserDetails(): Unit =
      authenticatedUserOpt.foreach(identityService.updateUserDetails(personalData))

    val userOrElseRegisterGuest: Future[UserIdData] =
      authenticatedUserOpt.map(authenticatedUser => Future {
        RegisteredUser(authenticatedUser.user)
      }).getOrElse {
        logger.info(s"User does not have an Identity account. Creating a guest account")
        identityService.registerGuest(personalData)
      }

    val validPromoCode =
      for {
        code <- subscriptionData.suppliedPromoCode
        promotion <- promoService.findPromotion(code)
        if promotion.validateFor(subscriptionData.productRatePlanId, personalData.address.country).isRight
      } yield code

    def sendEmail(contactId: ContactId, accountId: AccountId) = {
      subscriptionService.unsafeGetPaid(contactId)
        .zip(paymentService.paymentMethod(accountId))
        .flatMap { case (subscription, paymentMethod) =>
          subscriptionService.billingSchedule(subscription).map { schedule =>
            lazy val exception = new NoSuchElementException(s"Could not make a billing schedule for subscription ${subscription.name.get}")
            schedule.map { s =>
              exactTargetService.sendETDataExtensionRow(s, paymentMethod, subscription, subscriptionData)
            }.getOrElse(Future.failed(exception))
          }
        } recover { case err: Throwable =>
          logger.error(s"Error while trying to send a confirmation email to account ${accountId.get}", err)
        }
    }

    for {
      userData <- userOrElseRegisterGuest
      contactId <- salesforceService.createOrUpdateUser(personalData, userData.id)
      payment = subscriptionData.paymentData match {
        case paymentData@DirectDebitData(_, _, _) =>
          paymentService.makeDirectDebitPayment(paymentData, personalData, contactId)
        case paymentData@CreditCardData(_) =>
          val plan = catalogService.digipackCatalog.unsafeFind(subscriptionData.productRatePlanId)
          paymentService.makeCreditCardPayment(paymentData, personalData, userData, contactId, plan)
      }
      method <- payment.makePaymentMethod
      result <- zuoraService.createSubscription(
        subscribeAccount = payment.makeAccount,
        paymentMethod = Some(method),
        productRatePlanId = subscriptionData.productRatePlanId,
        name = personalData,
        address = personalData.address,
        promoCode = validPromoCode,
        paymentDelay = Some(zuoraProperties.paymentDelayInDays),
        ipAddressOpt = requestData.ipAddress.map(_.getHostAddress))
    } yield {
      updateAuthenticatedUserDetails()
      sendEmail(contactId, AccountId(result.accountId))
      CheckoutResult(contactId, userData, result)
    }
  }
}
