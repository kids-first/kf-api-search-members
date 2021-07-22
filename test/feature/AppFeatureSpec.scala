package feature

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import utils.{MemberDocument, WithJwtKeys, WithMemberIndex}

import scala.concurrent.ExecutionContext.Implicits.global

class AppFeatureSpec extends PlaySpec with GuiceOneServerPerSuite with ScalaFutures with WithJwtKeys with BeforeAndAfterAll with WithMemberIndex {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))
    
  var token: String = ""
  override def beforeAll(): Unit = {

    val members = Seq(
      MemberDocument(_id = "a1", firstName = "John", lastName = "Doe", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("research"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain"), bio = Some("my Bio bla john bla"), story = Some("My Story john bla")),
      MemberDocument(_id = "a2", firstName = "Jane", lastName = "River", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("community"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain")),
      MemberDocument(_id = "a3", firstName = "John", lastName = "Gray", email = Some("jdoeemail@gmail.com"), institution = Some("CHUSJ"), country = Some("Canada"), roles = List("research"), _title = Some("Dr."), city = Some("Montreal"), state = Some("Quebec"), interests = List("Cancer Brain Left Side")),
      MemberDocument(_id = "private_member", firstName = "Private John", lastName = "Private Doe", email = Some("privatejdoeemail@gmail.com"), isPublic = false, roles = List("research"), interests = List("Cancer Brain", "Private Stuff")),
      MemberDocument(_id = "not_accepted_terms", firstName = "NotAcceptedTerms John", lastName = "NotAcceptedTerms Doe", email = Some("notacceptedtermsjdoeemail@yahoo.com"), acceptedTerms = false, roles = List("community"))
    )
    populateIndex(members)

    val wsClient = app.injector.instanceOf[WSClient]
    token = getKeycloakToken(wsClient).futureValue
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(Map(
    "keycloak.certs_url" -> s"$keycloakTestHost/auth/realms/master/protocol/openid-connect/certs",
    "keycloak.realm_info_url" -> s"$keycloakTestHost/auth/realms/master")
  ).build()

  "Test / should return 200" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/"
    whenReady(wsClient.url(statusUrl).get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status must be(200)
    }

  }


  "Test /search should return results" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=john&role=research&start=0&end=20&interest=Cancer%20Brain&qAllMembers=true"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        logger.error(s"Response ${response.status}")
        response.status must be(200)
        response.json mustEqual Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1,
            "interests" -> Json.obj(
              "Cancer Brain" -> 1,
              "Cancer Brain Left Side" -> 1
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
                "firstName" -> Json.arr("<em>John</em>"),
                "bio" -> Json.arr("my Bio bla <em>john</em> bla"),
                "story" -> Json.arr("My Story <em>john</em> bla")),
              "hashedEmail" -> md5HashString("jdoeemail@gmail.com"),
              "city" -> "Montreal",
              "roles" -> Json.arr("research"),
              "state" -> "Quebec",
              "_id" -> "a1",
              "interests" -> Json.arr("Cancer Brain"),
              "title" -> "Dr.",
              "isPublic" -> true,
              "isActive" -> true
            )
          )
        )
    }
  }

  "Test /search for all members as USER should return only public and accepted terms and active results" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/searchmembers?queryString=john&role=research&start=0&end=20&interest=Cancer%20Brain&qAllMembers=true"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustEqual Json.obj(
          "count" -> Json.obj(
            "total" -> 2,
            "public" -> 1,
            "private" -> 1,
            "interests" -> Json.obj(
              "Cancer Brain" -> 1,
              "Cancer Brain Left Side" -> 1,
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
                "firstName" -> Json.arr("<em>John</em>"),
                "bio" -> Json.arr("my Bio bla <em>john</em> bla"),
                "story" -> Json.arr("My Story <em>john</em> bla")),
              "hashedEmail" -> md5HashString("jdoeemail@gmail.com"),
              "city" -> "Montreal",
              "roles" -> Json.arr("research"),
              "state" -> "Quebec",
              "_id" -> "a1",
              "interests" -> Json.arr("Cancer Brain"),
              "title" -> "Dr.",
              "isPublic" -> true,
              "isActive" -> true
            )

          )
        )
    }
  }

  "Test /search with empty queryString should return results with highlights empty" in {
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
              "Cancer Brain" -> 1,
              "Cancer Brain Left Side" -> 1
            ),
            "interestsOthers" -> 0,
            "roles" -> Json.obj(
              "research" -> 1,
              "patient" -> 0,
              "health" -> 0,
              "community" -> 1
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
              "hashedEmail" -> md5HashString("jdoeemail@gmail.com"),
              "isPublic" -> true,
              "isActive" -> true
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

  "Test /interests should return list of interests" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/interests?queryString=can"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "interests" -> Json.arr("Cancer Brain", "Cancer Brain Left Side")
        )
    }
  }

  "Test /interests without token should return 401" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/interests?queryString=can"
    whenReady(wsClient.url(statusUrl).get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 401
    }
  }

  "Test /interests_stats should return list of interests" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/interests_stats"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "interests" -> Json.arr(
            Json.obj("name" -> "Cancer Brain", "count" -> 3),
            Json.obj("name" -> "Cancer Brain Left Side", "count" -> 1),
            Json.obj("name" -> "Private Stuff", "count" -> 1),
          )
        )
    }
  }

  "Test /interests_stats with a size should return list of interests" in {
    val wsClient = app.injector.instanceOf[WSClient]
    val statusUrl = s"http://localhost:$port/interests_stats?size=1"
    whenReady(wsClient.url(statusUrl).addHttpHeaders("Authorization" -> s"Bearer $token").get(), Timeout(Span(10, Seconds))) {
      response =>
        response.status mustBe 200
        response.json mustBe Json.obj(
          "interests" -> Json.arr(
            Json.obj("name" -> "Cancer Brain", "count" -> 3),
            Json.obj("name" -> "Others", "count" -> 2)
          )
        )
    }
  }

}