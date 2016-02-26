package configuration

import com.github.nscala_time.time.Imports._
import com.gocardless.GoCardlessClient
import com.gocardless.GoCardlessClient.Environment
import com.gu.cas.PrefixedTokens
import com.gu.config.{DiscountRatePlanIds, DigitalPackRatePlanIds, MembershipRatePlanIds, ProductFamilyRatePlanIds}
import com.gu.identity.cookie.{PreProductionKeys, ProductionKeys}
import com.gu.memsub.auth.common.MemSub.Google._
import com.gu.memsub.promo._
import com.gu.memsub.{Digipack, Membership}
import com.gu.monitoring.StatusMetrics
import com.gu.salesforce.SalesforceConfig
import com.gu.subscriptions.{CASApi, CASService}
import com.netaporter.uri.dsl._
import com.typesafe.config.ConfigFactory
import monitoring.Metrics
import net.kencochrane.raven.dsn.Dsn
import play.api.mvc.{Call, RequestHeader}

import scala.util.Try

object Config {
  val appName = "subscriptions-frontend"
  val config = ConfigFactory.load()

  val playSecret = config.getString("play.crypto.secret")

  lazy val googleAuthConfig = googleAuthConfigFor(config)

  lazy val googleGroupChecker = googleGroupCheckerFor(config)

  val stage = config.getString("stage")
  val stageProd: Boolean = stage == "PROD"


  val trackerUrl = config.getString("snowplow.url")
  val bcryptSalt = config.getString("activity.tracking.bcrypt.salt")
  val bcryptPepper = config.getString("activity.tracking.bcrypt.pepper")

 object Identity {
    private val idConfig = config.getConfig("identity")

    val baseUri = idConfig.getString("baseUri")
    val apiToken = idConfig.getString("apiToken")

    val keys = if (idConfig.getBoolean("production.keys")) new ProductionKeys else new PreProductionKeys

    val testUsersSecret = idConfig.getString("test.users.secret")

    val webAppUrl = idConfig.getString("webapp.url")

    val webAppProfileUrl = webAppUrl / "account" / "edit"

    val sessionDomain = idConfig.getString("sessionDomain")

    def idWebAppSigninUrl(returnTo: Call)(implicit request: RequestHeader) = idWebAppUrl("signin", returnTo)

    def idWebAppSignOutUrl(returnTo: Call)(implicit request: RequestHeader) = idWebAppUrl("signout", returnTo)

    def idWebAppRegisterUrl(returnTo: Call)(implicit request: RequestHeader) = idWebAppUrl("register", returnTo)

    def idWebAppUrl(idPath: String, returnTo : Call)(implicit request: RequestHeader): String =
      (webAppUrl / idPath) ? ("returnUrl" -> returnTo.absoluteURL(secure = true)) ? ("skipConfirmation" -> "true")

  }

  object Zuora {
    private val stageConfig = config.getConfig("touchpoint.backend.environments").getConfig(stage)

    val paymentDelay = stageConfig.getInt("zuora.paymentDelayInDays").days
  }

  val subscriptionsUrl = config.getString("subscriptions.url")

  val sentryDsn = Try(new Dsn(config.getString("sentry.dsn")))

  lazy val Salesforce =  SalesforceConfig.from(config.getConfig("touchpoint.backend.environments").getConfig(stage), stage)

  object GoCardless {
    private val token = config.getString("gocardless.token")
    val client = GoCardlessClient.create(token, Environment.SANDBOX)
  }

  object ExactTarget {
    val clientId = config.getString("exact-target.client-id")
    val clientSecret = config.getString("exact-target.client-secret")
    val welcomeTriggeredSendKey = config.getString("exact-target.triggered-send-keys.welcome")
  }

  def digipackRatePlanIds(env: String): DigitalPackRatePlanIds =
    DigitalPackRatePlanIds.fromConfig(ProductFamilyRatePlanIds.config(Some(config))(env, Digipack))

  def discountRatePlanIds(env: String): DiscountRatePlanIds =
    DiscountRatePlanIds.fromConfig(config.getConfig(s"touchpoint.backend.environments.$env.zuora.ratePlanIds")
  )

  def membershipRatePlanIds(env: String) =
    MembershipRatePlanIds.fromConfig(ProductFamilyRatePlanIds.config(Some(config))(env, Membership))

  def demoPromo(env: String) = {
    val prpIds = digipackRatePlanIds(env)
    Promotion(
      appliesTo = AppliesTo.ukOnly(Set(
        prpIds.digitalPackMonthly,
        prpIds.digitalPackQuaterly,
        prpIds.digitalPackYearly
      )),
      campaignName = "DigiPack - Free £30 digital gift card",
      codes = PromoCodeSet(PromoCode("DGA88"), PromoCode("DGB88")),
      description = "Get £30 to spend with a top retailer of your choice when you subscribe. Use your digital gift card at John Lewis, Amazon, M&S and more. Treat yourself or a friend.",
      expires = new LocalDate(2016,4,1).toDateTime(LocalTime.Midnight, DateTimeZone.forID("Europe/London")),
      imageUrl = "https://media.guim.co.uk/076bb31be49a31dfe82869ed2937fc8254917361/0_0_850_418/850.jpg",
      promotionType = Incentive,
      redemptionInstructions = "We'll send redemption instructions to your registered email address",
      roundelHtml = "Free <span class='roundel__strong'>£30</span> digital gift card",
      thumbnailUrl = "http://lorempixel.com/46/16/abstract",
      title = "Free £30 digital gift card when you subscribe"
    )
  }

  def discountPromo(env: String): Option[Promotion] = {
    val prpIds = digipackRatePlanIds(env)
    Some(Promotion(
      appliesTo = AppliesTo.ukOnly(Set(
        prpIds.digitalPackMonthly,
        prpIds.digitalPackQuaterly,
        prpIds.digitalPackYearly
      )),
      campaignName = "DigiPack - 17% off for 3 months",
      codes = PromoCodeSet(PromoCode("17OFF")),
      description = "Get 17% off for 3 months when you subscribe.",
      expires = new LocalDate(2016,4,1).toDateTime(LocalTime.Midnight, DateTimeZone.forID("Europe/London")),
      imageUrl = "https://media.guim.co.uk/9ee88fc2f08bc23e69e2e11a4d4964f4120c6725/0_0_850_418/850.jpg",
      redemptionInstructions = "We'll send redemption instructions to your registered email address",
      roundelHtml = "Free <span class='roundel__strong'>17%</span> off",
      thumbnailUrl = "http://lorempixel.com/46/16/abstract",
      title = "DigiPack - 17% off for 3 months",
      promotionType = PercentDiscount(3, 17)
    )).filter(_ => env != "PROD")
  }

  object CAS {
    lazy val casConf = config.getConfig("cas")

    lazy val url = casConf.getString("url")
    lazy val emergencyEncoder = {
      val conf = casConf.getConfig("emergency.subscriber.auth")
      val prefix = conf.getString("prefix")
      val secret = conf.getString("secret")
      PrefixedTokens(secretKey = secret, emergencySubscriberAuthPrefix = prefix)
    }
  }

  lazy val casService = {
    val metrics = new StatusMetrics with Metrics {
      override val service: String = "CAS service"
    }
    val api = new CASApi(CAS.url, metrics)
    new CASService(api)
  }
}
