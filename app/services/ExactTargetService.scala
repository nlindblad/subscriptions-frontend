package services

import akka.agent.Agent
import com.gu.memsub.Subscription.Paid
import com.gu.memsub.{PaymentMethod, Subscription}
import com.gu.services.model.{BillingSchedule, PaymentDetails}
import com.squareup.okhttp.Request.Builder
import com.squareup.okhttp.{MediaType, OkHttpClient, RequestBody, Response}
import com.typesafe.scalalogging.LazyLogging
import configuration.Config
import model.SubscriptionData
import model.exactTarget.SubscriptionDataExtensionRow
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._
class ExactTargetService extends LazyLogging {
  lazy val etClient: ETClient = ETClient

  def sendETDataExtensionRow(billingSchedule: BillingSchedule,
                             paymentMethod: PaymentMethod,
                             subscription: Subscription with Paid,
                             subscriptionData: SubscriptionData): Future[Unit] = {
    val row = SubscriptionDataExtensionRow(billingSchedule, paymentMethod, subscription, subscriptionData)
    etClient.sendSubscriptionRow(row).map { response =>
      val email = subscriptionData.personalData.email
      response.code() match {
        case 202 =>
          logger.info(s"Successfully sent an email to confirm the subscription to $email")
        case _ =>
          logger.error(
            s"Failed to send the subscription email to $email. Code: ${response.code()}, Message: ${response.body.string()}")
      }
    }
  }
}

trait ETClient {
  def sendSubscriptionRow(row: SubscriptionDataExtensionRow): Future[Response]
}

object ETClient extends ETClient with LazyLogging {
  private val config = Config.ExactTarget
  private val clientId = config.clientId
  private val clientSecret = config.clientSecret
  lazy val welcomeTriggeredSendKey = Config.ExactTarget.welcomeTriggeredSendKey
  private val jsonMT = MediaType.parse("application/json; charset=utf-8")
  private val httpClient = new OkHttpClient()
  private val authEndpoint = "https://auth.exacttargetapis.com/v1/requestToken"
  private val restEndpoint = "https://www.exacttargetapis.com/messaging/v1"

  private val accessToken = Agent(getAccessToken)
  import play.api.Play.current

  /**
   * Get a token from Exact Target, that is expired every hour
   * See https://code.exacttarget.com/apis-sdks/rest-api/using-the-api-key-to-authenticate-api-calls.html
   * This call is blocking
   */
  private def getAccessToken: String = {
    val payload = Json.obj(
      "clientId" -> clientId,
      "clientSecret" -> clientSecret
    ).toString()

    val body = RequestBody.create(jsonMT, payload)
    val request = new Builder().url(authEndpoint).post(body).build()
    val response = httpClient.newCall(request).execute()

    val respBody = response.body().string()

    logger.info("Got new token: " + (Json.parse(respBody) \ "accessToken").as[String])
    (Json.parse(respBody) \ "accessToken").as[String]
  }

  Akka.system.scheduler.schedule(initialDelay = 30.minutes, interval = 30.minutes) {
    accessToken send getAccessToken
  }

  /**
   * See https://code.exacttarget.com/apis-sdks/rest-api/v1/messaging/messageDefinitionSends.html
   */
  override def sendSubscriptionRow(row: SubscriptionDataExtensionRow): Future[Response] = {

    def endpoint = s"$restEndpoint/messageDefinitionSends/$welcomeTriggeredSendKey/send"

    Future {
      val payload = Json.obj(
        "To" -> Json.obj(
          "Address" -> row.email,
          "SubscriberKey" -> row.email,
          "ContactAttributes" -> Json.obj(
            "SubscriberAttributes" ->  Json.toJsFieldJsValueWrapper(row.fields.toMap)
          )
        )
      ).toString()

      val body = RequestBody.create(jsonMT, payload)
      val request = new Builder()
                          .url(endpoint)
                          .post(body)
                          .header("Authorization", s"Bearer ${accessToken.get()}")
                          .build()
      val response = httpClient.newCall(request).execute()

      response
    }

  }
}
