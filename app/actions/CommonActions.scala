package actions

import com.gu.googleauth.UserIdentity
import com.gu.{googleauth => GuAuth}
import controllers.{Cached, NoCache}
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import configuration.Config.QA.{passthroughCookie, userIdentity}

object CommonActions {

  val NoCacheAction = resultModifier(noCache)

  val GoogleAuthAction = OAuthActions.AuthAction

  val GoogleAuthenticatedStaffAction = NoCacheAction andThen GoogleAuthAction

  val CachedAction = resultModifier(Cached(_))

  val QACookieAuthAction = NoCacheAction andThen QACookieAuth

  def resultModifier(f: Result => Result) = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = block(request).map(f)
  }

  def noCache(result: Result): Result = NoCache(result)


  type OAuthRequest[A] = AuthenticatedRequest[A, Option[UserIdentity]]

  object QACookieAuth extends ActionBuilder[OAuthRequest] {
    override def invokeBlock[A](request: Request[A], block: (OAuthRequest[A]) => Future[Result]): Future[Result] = {
      if (request.cookies.exists(_ == passthroughCookie)) {
        block(new AuthenticatedRequest(Some(userIdentity), request))
      } else {
        block(GuAuth.AuthenticatedRequest(request))
      }
    }
  }
}
