package services

import java.net.URL

import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.testkit.DockerTests
import com.sksamuel.elastic4s.{ElasticsearchClientUri, Index, Indexable, RefreshPolicy}
import models.{MemberDocument, QueryFilter}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.StubControllerComponentsFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.Try

class ESQueryServiceSpec extends FlatSpec with DockerTests with Matchers with BeforeAndAfterAll with StubControllerComponentsFactory with MockitoSugar {

  var configuration: Configuration = _
  var esQueryService: ESQueryService = _

  override def beforeAll(): Unit = {

    val elasticsearchClientUri =  ElasticsearchClientUri("localhost", 9200)
    val esClient = HttpClient(elasticsearchClientUri)

    Try {
      esClient.execute {
        deleteIndex(IndexName)
      }.await

      esClient.execute {
        createIndex(IndexName).mappings(
          mapping(IndexName)
            .fields(
              textField("firstName").fields(keywordField("raw")),
              textField("lastName").fields(keywordField("raw")),
              keywordField("email"),
              textField("institutionalEmail"),
              keywordField("acceptedTerms"),
              booleanField("isPublic"),
              keywordField("roles"),
              keywordField("title"),
              textField("jobTitle").fields(keywordField("raw")),
              textField("institution").fields(keywordField("raw")),
              textField("city").fields(keywordField("raw")),
              textField("state").fields(keywordField("raw")),
              textField("country").fields(keywordField("raw")),
              keywordField("eraCommonsID"),
              textField("bio"),
              textField("story"),
              textField("interests").fields(keywordField("raw"))
              //        textField("doc.virtualStudies") FIXME
            )
        )
      }.await

      esClient.execute(
        bulk(
          indexRequest("a1", MemberDocument("a1", "John", "DoeC", Some("jdoeemail@gmail.com"), isPublic = true, roles = List("Community", "Research"), _title = Some("Dr."), None, None, None, None, Nil)),
          indexRequest("a2", MemberDocument("a2", "John", "DoeA", Some("jdoeemail@gmail.com"), isPublic = true, roles = List("Research"), _title = Some("M."), None, None, None, None, Nil)),
          indexRequest("b1", MemberDocument("b1", "JohnC", "DoeB", Some("jdoeemail@gmail.com"), isPublic = true, roles = List("Community"), _title = Some("Dr."), None, None, None, None, Nil)),
          indexRequest("b2", MemberDocument("b2", "Doe", "John", Some("djohnemail@gmail.com"), isPublic = true, roles = Nil, _title = None, None, None, None, None, List("cancer", "pandas"))),
          indexRequest("c1", MemberDocument("c1", "Doe", "John", Some("djohnemail@yahoo.com"), isPublic = true, roles = Nil, _title = None, None, None, None, None, List("cancer", "pandas")))
        ).refresh(RefreshPolicy.Immediate)
      ).await
    }

    configuration = Configuration.apply("elasticsearch.host"-> "localhost", "elasticsearch.ports" -> List(9200))

    esQueryService = new ESQueryService(configuration)
  }


  private val IndexName = "member"

//  Can't use Writes of MemberDocument directly (need to remove parameter "_id", need to create a new one...
//  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>  Json.toJson(t).toString()
  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>  Json.obj(
    "firstName" -> t.firstName,
    "lastName" -> t.lastName,
    "email" -> t.email,
    "isPublic" -> t.isPublic,
    "roles" -> t.roles,
    "title" -> t._title,
    "institution" -> t.institution,
    "city"-> t.city,
    "state"-> t.state,
    "country"-> t.country,
    "interests"-> t.interests
  ).toString()


  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  "ESQueryService" should "order documents by score" in {
    val result = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.map(r => r.score).toSeq
    result shouldBe result.sorted(Ordering.Float.reverse)
  }

  it should "order documents by lastName for same score" in {
    val hitResultsByScore = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.toSeq.groupBy(_.score).values.toSeq
    val searchHitLastNames =  hitResultsByScore.map(_.map(s => (Json.parse(s.sourceAsString).as[JsObject] ++ Json.obj("_id" -> s.id)).as[MemberDocument].lastName))

    searchHitLastNames.foreach(l =>  l shouldBe l.sorted(Ordering.String))
  }

  it should "enable Email to be searchable" in {
//    esQueryService.generateFilterQueries(QueryFilter("gmail", 0, 10)).await.right.get.result.hits.hits.toSeq.foreach(p => println((Json.parse(p.sourceAsString).as[JsObject] ++ Json.obj("_id" -> p.id)).as[MemberDocument]))
    esQueryService.generateFilterQueries(QueryFilter("gmail", 0, 10)).await.right.get.result.hits.hits.toSeq.size shouldBe 4
  }
}