package utils

import java.net.URL
import java.time.Clock

import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}

import scala.io.Source


trait WithJwtKeys {
  implicit val clock: Clock = Clock.systemUTC
  val publicKeyUrl: String = getClass.getClassLoader.getResource("public_key.pub").toString
  val privateKey: String = {
    val privateKeyUrl: URL = getClass.getClassLoader.getResource("private_key.pem")
    val source = Source.fromURL(privateKeyUrl)
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  def generateToken(expiredIn: Int = 1000, issuedAt: Int = 0, id: String = "12345", by: String = "ego", about: String = "12345", content: String = ""): String = {
    val claim = JwtClaim().by(by).expiresIn(expiredIn).withId(id).issuedAt(issuedAt).about(about).withContent(content)
    JwtJson.encode(claim, privateKey, JwtAlgorithm.RS256)
  }
}