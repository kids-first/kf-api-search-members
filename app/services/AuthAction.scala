package services

import javax.inject.Inject
import pdi.jwt.JwtClaim
import play.api.http.HeaderNames
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


case class UserRequest[A](jwt: JwtClaim, token: String, request: Request[A], isAdmin: Boolean) extends WrappedRequest[A](request)

class AuthAction @Inject()(bodyParser: BodyParsers.Default, authService: AuthService)(implicit ec: ExecutionContext)
  extends ActionBuilder[UserRequest, AnyContent] {

  override def parser: BodyParser[AnyContent] = bodyParser

  override protected def executionContext: ExecutionContext = ec

  // A regex for parsing the Authorization header value
  private val headerTokenRegex = """Bearer (.+?)""".r

  override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
    extractBearerToken(request) map { token =>
      authService.validateJwt(token) match {
        case Success(claim) => block(UserRequest(claim, token, request, isAdmin = checkAdmin(claim)))      // token was valid - proceed!
        case Failure(t) => Future.successful(Results.Unauthorized(t.getMessage))  // token was invalid - return 401
      }
    } getOrElse Future.successful(Results.Unauthorized)     // no token was sent - return 401

  // Helper for extracting the token value
  private def extractBearerToken[A](request: Request[A]): Option[String] =
    request.headers.get(HeaderNames.AUTHORIZATION) collect {
      case headerTokenRegex(token) => token
    }

  private def checkAdmin(claim: JwtClaim): Boolean = {
    (Json.parse(claim.toJson) \\ "roles").headOption match {
      case Some(v) => v.validate[Seq[String]] match {
        case JsSuccess(value, _) => value.contains("ADMIN")
        case _: JsError => false
      }
      case None => false
    }
  }

}

