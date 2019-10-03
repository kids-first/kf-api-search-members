package services

import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.testkit.DockerTests
import com.sksamuel.elastic4s.{ElasticsearchClientUri, Index, Indexable, RefreshPolicy}
import models.{MemberDocument, QueryFilter}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.StubControllerComponentsFactory

import scala.concurrent.ExecutionContext.Implicits.global
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
              //        textField("doc.Roles"), FIXME
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
          indexRequest("1", MemberDocument("John", "DoeC", Some("jdoeemail@gmail.com"), isPublic = true, None, None, None, None, Nil)),
          indexRequest("2", MemberDocument("John", "DoeA", Some("jdoeemail@gmail.com"), isPublic = true, None, None, None, None, Nil)),
          indexRequest("3", MemberDocument("JohnC", "DoeB", Some("jdoeemail@gmail.com"), isPublic = true, None, None, None, None, Nil)),
          indexRequest("4", MemberDocument("Doe", "John", Some("jdoeemail@gmail.com"), isPublic = true, None, None, None, None, List("cancer", "pandas")))
        ).refresh(RefreshPolicy.Immediate)
      ).await
    }

    configuration = Configuration.apply("elasticsearch.host"-> "localhost", "elasticsearch.ports" -> List(9200))

    esQueryService = new ESQueryService(configuration)
  }


  private val IndexName = "member"


  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>  Json.toJson(t).toString()


  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  "ESQueryService" should "order documents by score" in {
      val result = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.map(r => r.score).toSeq
    result shouldBe result.sorted(Ordering.Float.reverse)
  }

  it should "order documents by lastName for same score" in {
    val hitResultsByScore = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.toSeq.groupBy(_.score).values.toSeq

    val searchHitLastNames =  hitResultsByScore.map(_.map(s => Json.parse(s.sourceAsString).as[MemberDocument].lastName))

    searchHitLastNames.foreach(l =>  l shouldBe l.sorted(Ordering.String))
  }
}
