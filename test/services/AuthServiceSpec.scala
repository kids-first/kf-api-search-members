package services

import org.scalatest.{FlatSpec, Matchers}
import play.api.Configuration
import utils.WithJwtKeys

import java.security.PublicKey
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthServiceSpec extends FlatSpec with Matchers with WithJwtKeys {

  val issuer = "http://localhost:8888/auth/realms/KidsFirst"
  val configuration: Configuration = Configuration.from(Map("keycloak.realm_info_url" -> issuer))
  val publicKeys: Future[Map[String, PublicKey]] = Future.successful(Map("kid1" -> publicKey))

  "verifyToken" should "return Success" in {
    val validToken = generateToken(expiredIn = 3600, by = issuer)
    val authService = new AuthService(configuration)
    authService.verifyToken(validToken, publicKeys)
      .map(result => result should not be None)
  }

  it should "return Failure if wrong token" in {
    val authService = new AuthService(configuration)
    authService.verifyToken("wrong token", publicKeys)
      .map(result => result should be (None))
  }

  it should "return Failure if expired token" in {
    val expiredToken = generateToken(expiredIn = -3600, issuedAt = -7200)
    val authService = new AuthService(configuration)

    authService.verifyToken(expiredToken, publicKeys)
      .map(result => result should be (None))
  }

}
