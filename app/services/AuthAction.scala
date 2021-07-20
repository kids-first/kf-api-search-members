package services

import org.keycloak.representations.AccessToken
import play.api.http.HeaderNames
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


case class UserRequest[A](jwt: AccessToken, token: String, request: Request[A], isAdmin: Boolean) extends WrappedRequest[A](request)

class AuthAction @Inject()(bodyParser: BodyParsers.Default, authService: AuthService, keyService: KeyService)(implicit ec: ExecutionContext)
  extends ActionBuilder[UserRequest, AnyContent] {

  override def parser: BodyParser[AnyContent] = bodyParser

  override protected def executionContext: ExecutionContext = ec

  // A regex for parsing the Authorization header value
  private val headerTokenRegex = """Bearer (.+?)""".r

  override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] = {
    extractBearerToken(request) match {
      case Some(token) => {
        authService.verifyToken(token, keyService.apply()).flatMap(decodedAccessToken => decodedAccessToken match {
          case Some(validatedAccessToken) => {
            block(UserRequest(validatedAccessToken, token, request, isAdmin = checkAdmin(validatedAccessToken)))
          } // token was valid - proceed!
          case None => Future.successful(Results.Unauthorized) // token was invalid - return 401
        })
      }
      case None => Future.successful(Results.Unauthorized) // no token - return 401
    }
  }

  // Helper for extracting the token value
  private def extractBearerToken[A](request: Request[A]): Option[String] =
    request.headers.get(HeaderNames.AUTHORIZATION) collect {
      case headerTokenRegex(token) => token
    }

  private def checkAdmin(accessToken: AccessToken): Boolean = {
    if(accessToken.getRealmAccess() == null) return false
    accessToken.getRealmAccess().getRoles.contains("ADMIN")
  }

}

