package utils

import org.keycloak.jose.jws.AlgorithmType
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtJson}
import play.api.libs.ws.{WSClient, WSResponse}
import services.KeyData

import java.net.URL
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.time.Clock
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source


trait WithJwtKeys {
  implicit val clock: Clock = Clock.systemUTC
  val publicKeyUrl: String = getClass.getClassLoader.getResource("public_key.pub").toString
  val publicKey: PublicKey = {
    val publicKeyUrl: URL = getClass.getClassLoader.getResource("public_key.pub")
    val source = stripPublicKeyText(Source.fromURL(publicKeyUrl).mkString)
    val byteKey = Base64.getDecoder().decode(source.getBytes())
    val X509publicKey = new X509EncodedKeySpec(byteKey)
    val keyFactory = KeyFactory.getInstance(AlgorithmType.RSA.toString)
    keyFactory.generatePublic(X509publicKey)
  }
  val privateKey: String = {
    val privateKeyUrl: URL = getClass.getClassLoader.getResource("private_key.pem")
    val source = Source.fromURL(privateKeyUrl)
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  private def stripPublicKeyText(keyText: String): String =
    keyText
      .stripMargin
      .replace("\n", "")
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----END PUBLIC KEY-----", "")

  def generateToken(expiredIn: Int = 1000, issuedAt: Int = 0, id: String = "12345", by: String = "ego", about: String = "12345", content: String = "", keyId: String = "kid1"): String = {
    val claim = JwtClaim().by(by).expiresIn(expiredIn).withId(id).issuedAt(issuedAt).about(about).withContent(content).+("typ", "Bearer")
    val header = JwtHeader.apply(JwtAlgorithm.RS256).withKeyId(keyId).withType("JWT")
    JwtJson.encode(header, claim, privateKey)
  }

  def getKeycloakToken(ws: WSClient)(implicit ec: ExecutionContext): Future[String] = {
    val params: Seq[(String, String)] = Seq(("username", "admin"), ("password", "admin"), ("grant_type", "password"), ("client_id", "admin-cli"))
    val response: Future[WSResponse] = ws
      .url("http://localhost:8080/auth/realms/master/protocol/openid-connect/token")
      .withHttpHeaders("Content-type" -> "application/x-www-form-urlencoded")
      .post(params.map { case (k, v) => s"$k=$v" }.mkString("&"))
    val futureToken = response.map { body =>
      (body.json \ "access_token").as[String]
    }
    futureToken
  }
}