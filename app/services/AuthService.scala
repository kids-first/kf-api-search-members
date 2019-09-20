package services

import java.time.Clock

import javax.inject.Inject
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.Configuration

import scala.io.Source
import scala.util.{Failure, Success, Try}

class AuthService @Inject()(config: Configuration) {

  implicit val clock: Clock = Clock.systemUTC

  def validateJwt(token: String): Try[JwtClaim] = for {
      claims <- JwtJson.decode(token, AuthService.key, Seq(JwtAlgorithm.RS256)) // Decode the token using the secret key
      _ <- validateClaims(claims)     // validate the data stored inside the token
    } yield claims

  private val validateClaims = (claims: JwtClaim) =>
    if (claims.isValid(clock)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }
}

object AuthService {
  import Control._

  private val key = using(Source.fromURL("https://ego-qa.kidsfirstdrc.org/oauth/token/public_key")) { source => source.mkString}
}

object Control {
  def using[A <: { def close(): Unit }, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      resource.close()
    }
}
