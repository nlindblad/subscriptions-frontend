package controllers

import com.typesafe.scalalogging.LazyLogging
import play.api.mvc._
import services.TouchpointBackend
import play.api.libs.concurrent.Execution.Implicits._

object Healthcheck extends Controller with LazyLogging{

  def index = Action.async {
      (for {
       //TODO: Touch salesforce
        products <- TouchpointBackend.Normal.zuoraService.products if products.nonEmpty
      } yield Ok("OK"))
      .recover { case t: Throwable =>
        logger.error("Health check failed", t)
        ServiceUnavailable("Service Unavailable")
      }
      .map(Cached(1)(_))
  }

}
