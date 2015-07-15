package configuration

import com.gu.googleauth.{UserIdentity, GoogleAuthConfig}
import com.gu.identity.cookie.{PreProductionKeys, ProductionKeys}
import com.gu.membership.salesforce.SalesforceConfig
import com.netaporter.uri.dsl._
import com.typesafe.config.ConfigFactory
import net.kencochrane.raven.dsn.Dsn
import com.github.nscala_time.time.Imports._
import play.api.mvc.Cookie

import scala.util.Try

object Config {
  val appName = "subscriptions-frontend"
  val config = ConfigFactory.load()

  val playSecret = config.getString("play.crypto.secret")

  val googleAuthConfig = {
    val con = ConfigFactory.load().getConfig("google.oauth")
    GoogleAuthConfig(
      clientId = con.getString("client.id"),
      clientSecret = con.getString("client.secret"),
      redirectUrl = con.getString("callback"),
      domain = Some("guardian.co.uk") // Google App domain to restrict login
    )
  }

  val stage = config.getString("stage")
  val stageProd: Boolean = stage == "PROD"

  object QA {
    val userIdentity =
      UserIdentity(
        email = "qa@example.com",
        firstName = "QA Cookie",
        lastName = "Passthrough",
        exp = System.currentTimeMillis() * 2,
        sub = "",
        avatarUrl = None)

    val passthroughCookie =
      Cookie(
        name = config.getString("qa.passthrough-cookie.name"),
        value = config.getString("qa.passthrough-cookie.value"),
        httpOnly = true
      )
  }

  object Identity {
    private val idConfig = config.getConfig("identity")

    val baseUri = idConfig.getString("baseUri")
    val apiToken = idConfig.getString("apiToken")

    val keys = if (idConfig.getBoolean("production.keys")) new ProductionKeys else new PreProductionKeys

    val webAppUrl = idConfig.getString("webapp.url")

    val webAppProfileUrl = webAppUrl / "account" / "edit"

    def webAppSigninUrl(path: String): String =
      (webAppUrl / "signin") ? ("returnUrl" -> absoluteUrl(path)) ? ("skipConfirmation" -> "true")

    def idWebAppSignOutUrl(path: String): String =
      (webAppUrl / "signout") ? ("returnUrl" -> absoluteUrl(path)) ? ("skipConfirmation" -> "true")

    private def absoluteUrl(path: String): String = (subscriptionsUrl / path).toString()
  }

  object Zuora {
    val paymentDelay = config.getConfig("touchpoint.backend.environments").getConfig(stage)
      .getInt("zuora.paymentDelayInDays").days
  }

  val subscriptionsUrl = config.getString("subscriptions.url")

  val sentryDsn = Try(new Dsn(config.getString("sentry.dsn")))

  lazy val Salesforce =  SalesforceConfig.from(config.getConfig("touchpoint.backend.environments").getConfig(stage), stage)
}
