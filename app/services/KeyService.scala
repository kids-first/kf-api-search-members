package services

import org.keycloak.jose.jws.AlgorithmType
import play.api.Configuration
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.ws.{WSClient, WSResponse}

import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class KeyData(kid: String, n: String, e: String)

class KeyService @Inject()(ws: WSClient, config: Configuration)(implicit ec: ExecutionContext) extends (() => Future[Map[String, PublicKey]]) {

  implicit val KeyReads: Reads[KeyData] = (
    (JsPath \ "kid").read[String] and
      (JsPath \ "n").read[String] and
      (JsPath \ "e").read[String]
    ) (KeyData.apply _)

  lazy private val publicKeys: Future[Map[String, PublicKey]] = {
    val response: Future[WSResponse] = ws.url(config.get[String]("keycloak.certs_url")).get()
    val futureKeys = response.map { body =>
      (body.json \ "keys").as[Seq[KeyData]]
    }
    futureKeys.map(keys => keys.map(k => (k.kid, generateKey(k))).toMap)
  }

  override def apply(): Future[Map[String, PublicKey]] = publicKeys

  private def generateKey(keyData: KeyData): PublicKey = {
    val keyFactory = KeyFactory.getInstance(AlgorithmType.RSA.toString)
    val urlDecoder = Base64.getUrlDecoder
    val modulus = new BigInteger(1, urlDecoder.decode(keyData.n))
    val publicExponent = new BigInteger(1, urlDecoder.decode(keyData.e))
    keyFactory.generatePublic(new RSAPublicKeySpec(modulus, publicExponent))
  }
}
