package services

import org.scalatest.{FlatSpec, Matchers}
import play.api.Configuration
import utils.WithJwtKeys


class AuthServiceSpec extends FlatSpec with Matchers with WithJwtKeys {


  val configuration: Configuration = Configuration.from(Map("jwt.public_key.url" -> publicKeyUrl))

  "validateClaims" should "return Success" in {
    val validToken = generateToken(expiredIn = 3600)
    val authService = new AuthService(configuration)
    authService.validateJwt(validToken) should be a 'success
  }

  it should "return Failure if wrong token" in {
    val authService = new AuthService(configuration)
    authService.validateJwt("wrong token") should be a 'failure
  }

  it should "return Failure if expired token" in {
    val expiredToken = generateToken(expiredIn = -3600, issuedAt = -7200)
    val authService = new AuthService(configuration)

    authService.validateJwt(expiredToken) should be a 'failure
  }

}
