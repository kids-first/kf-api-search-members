package services

import java.time.Clock

import javax.inject.Inject
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.Configuration
import utils.Control.using

import scala.io.Source
import scala.util.{Failure, Success, Try}

class AuthService @Inject()(config: Configuration) {

  implicit val clock: Clock = Clock.systemUTC

  private val key = using(Source.fromURL(config.get[String]("jwt.public_key.url"))) { source => source.mkString }

  def validateJwt(token: String): Try[JwtClaim] = for {
    claims <- JwtJson.decode(token, key, Seq(JwtAlgorithm.RS256))
    _ <- validateClaims(claims)
  } yield claims

  private val validateClaims = (claims: JwtClaim) =>
    if (claims.isValid(clock)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }

}


