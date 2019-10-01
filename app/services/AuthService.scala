package services

import java.time.Clock

import javax.inject.Inject
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.Configuration

import scala.io.Source
import scala.util.{Failure, Success, Try}

class AuthService @Inject()(config: Configuration) {
  import Control._

  implicit val clock: Clock = Clock.systemUTC

  def validateJwt(token: String): Try[JwtClaim] = for {
      claims <- JwtJson.decode(token, key(config.get[String]("jwt.public_key.url")), Seq(JwtAlgorithm.RS256))
      _ <- validateClaims(claims)
    } yield claims

  private val validateClaims = (claims: JwtClaim) =>
    if (claims.isValid(clock)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }

  private val key = (path: String) => using(Source.fromURL(path)) { source => source.mkString}
}

object Control {
  def using[A <: { def close(): Unit }, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      resource.close()
    }
}
