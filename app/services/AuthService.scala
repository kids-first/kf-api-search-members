package services

import org.keycloak.RSATokenVerifier
import org.keycloak.representations.AccessToken
import play.api.{Configuration, Logging}

import java.security.PublicKey
import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthService @Inject()(config: Configuration)(implicit ec: ExecutionContext) extends Logging {

  implicit val clock: Clock = Clock.systemUTC

  val keycloakRealmInfoUrl = config.get[String]("keycloak.realm_info_url")

  def verifyToken(token: String, publicKeys: Future[Map[String, PublicKey]]): Future[Option[AccessToken]] = {
    try {
      val tokenVerifier = RSATokenVerifier.create(token).realmUrl(keycloakRealmInfoUrl)
      val tokenVerifierHeader = tokenVerifier.getHeader
      for {
        publicKey <- publicKeys.map(_.get(tokenVerifierHeader.getKeyId))
      } yield publicKey match {
        case Some(pk) =>
          try {
            val token = tokenVerifier.publicKey(pk).verify().getToken
            Some(token)
          } catch {
            case e: Exception => {
              logger.error(e.toString)
              None
            }
          }

        case None =>
          logger.warn(s"no public key found for id ${tokenVerifierHeader.getKeyId}")
          None
      }
    } catch {
      case e: Exception => {
        logger.error(e.toString)
        Future.successful(None)
      }
    }
  }
}


