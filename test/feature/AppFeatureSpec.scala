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
      MemberDocument(_id = "a1", firstName = "John", lastName = "Doe", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("research"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain"), bio = Some("my Bio bla jdoeemail bla"), story = Some("My Story jdoeemail bla")),
      MemberDocument(_id = "a2", firstName = "Jane", lastName = "River", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("community"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain")),
      MemberDocument(_id = "a3", firstName = "Jean", lastName = "Gray", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("research"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain Left Side")),
      MemberDocument("private_member", "Doe", "John", Some("jdoeemail@gmail.com"), isPublic = false, roles = List("research"), interests = List("Cancer Brain")),
      MemberDocument("not_accepted_terms", "Doe", "John", Some("jdoeemail@yahoo.com"), acceptedTerms = false, roles = List("research"))
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
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=jdoeemail&role=research&start=0&end=20&interest=Cancer%20Brain"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1,
            "interests" -> Json.obj(
              "Cancer Brain" -> 1
            ),
            "interestsOthers" -> 0,
            "roles" -> Json.obj(
              "research" -> 1,
              "patient" -> 0,
              "health" -> 0,
              "community" -> 0
            )
          ),
          "publicMembers" -> Json.arr(
            Json.obj(
              "institution" -> "CHUSJ",
              "country" -> "Canada",
              "lastName" -> "Doe",
              "firstName" -> "John",
              "highlight" -> Json.obj(
                "email" -> Json.arr("<em>jdoeemail@gmail.com</em>"),
                "bio" -> Json.arr("my Bio bla <em>jdoeemail</em> bla"),
                "story" -> Json.arr("My Story <em>jdoeemail</em> bla")),
              "city" -> "Montreal",
              "roles" -> Json.arr("research"),
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
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=&start=0&end=20&role=research&interest=Cancer%20Brain"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1,
            "interests" -> Json.obj(
              "Cancer Brain" -> 1
            ),
            "interestsOthers" -> 0,
            "roles" -> Json.obj(
              "research" -> 1,
              "patient" -> 0,
              "health" -> 0,
              "community" -> 0
            )
          ),
          "publicMembers" -> Json.arr(
            Json.obj(
              "institution" -> "CHUSJ",
              "country" -> "Canada",
              "lastName" -> "Doe",
              "firstName" -> "John",
              "highlight" -> Option.empty[String],
              "city" -> "Montreal",
              "roles" -> Json.arr("research"),
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