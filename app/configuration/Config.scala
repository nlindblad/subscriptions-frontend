package configuration

import com.gu.googleauth.GoogleAuthConfig
import com.gu.identity.cookie.{PreProductionKeys, ProductionKeys}
import com.gu.membership.salesforce.SalesforceConfig
import com.typesafe.config.ConfigFactory
import net.kencochrane.raven.dsn.Dsn

import scala.util.Try

object Config {
  val appName = "subscriptions-frontend"

  val config = ConfigFactory.load()

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

  val sentryDsn = Try(new Dsn(config.getString("sentry.dsn")))


  object Identity {
    private val idConfig = config.getConfig("identity")

    val baseUri = idConfig.getString("baseUri")
    val apiToken = idConfig.getString("apiToken")

    val idKeys = if (idConfig.getBoolean("production.keys")) new ProductionKeys else new PreProductionKeys
  }

  val Salesforce =  SalesforceConfig.from(config.getConfig("touchpoint.backend.environments").getConfig(stage), stage)
}
