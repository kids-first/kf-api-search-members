package feature

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import utils.{MemberDocument, WithJwtKeys, WithMemberIndex}

class AppFeatureSpec extends PlaySpec with GuiceOneServerPerSuite with ScalaFutures with WithJwtKeys with BeforeAndAfterAll with WithMemberIndex {

  override def beforeAll(): Unit = {

    val members = Seq(
      MemberDocument(_id = "a1", firstName = "John", lastName = "Doe", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("role1"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain")),
      MemberDocument(_id = "a2", firstName = "Jane", lastName = "River", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("role2"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain")),
      MemberDocument("private_member", "Doe", "John", Some("jdoeemail@gmail.com"), isPublic = false, roles = List("role1")),
      MemberDocument("not_accepted_terms", "Doe", "John", Some("jdoeemail@yahoo.com"), acceptedTerms = false, roles = List("role1"))
    )
    populateIndex(members)

  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(Map("jwt.public_key.url" -> publicKeyUrl)).build()

  "Test / should return 200" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/"
    whenReady(wsClient.url(statusUrl).get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
    }

  }


  "Test /search should return results" in {
    val token = generateToken()
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=jdoeemail&role=role1&start=0&end=20"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1),
          "publicMembers" -> Json.arr(
            Json.obj(
              "institution" -> "CHUSJ",
              "country" -> "Canada",
              "lastName" -> "Doe",
              "firstName" -> "John",
              "highlight" -> Json.obj("email" -> Json.arr("<em>jdoeemail@gmail.com</em>")),
              "city" -> "Montreal",
              "roles" -> Json.arr("role1"),
              "state" -> "Quebec",
              "_id" -> "a1",
              "interests" -> Json.arr("Cancer Brain"),
              "title" -> "Dr.",
              "email" -> "jdoeemail@gmail.com"

            )
          )
        )
    }

  }

  "Test /search with empty queryString should return results with highlights empty" in {
    val token = generateToken()
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=&start=0&end=20&role=role1"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1),
          "publicMembers" -> Json.arr(
            Json.obj(
              "institution" -> "CHUSJ",
              "country" -> "Canada",
              "lastName" -> "Doe",
              "firstName" -> "John",
              "highlight" -> Option.empty[String],
              "city" -> "Montreal",
              "roles" -> Json.arr("role1"),
              "state" -> "Quebec",
              "_id" -> "a1",
              "interests" -> Json.arr("Cancer Brain"),
              "title" -> "Dr.",
              "email" -> "jdoeemail@gmail.com"

            )
          )
        )
    }

  }

  "Test /search without token should return 401" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=jdoeemail&start=0&end=20"
    whenReady(wsClient.url(statusUrl).get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 401
    }
  }


}