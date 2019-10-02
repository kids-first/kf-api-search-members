package services

import java.net.URL

import com.dimafeng.testcontainers.GenericContainer
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
import scala.io.Source
import scala.util.Try

class ESQueryServiceSpec extends FlatSpec with DockerTests with Matchers with BeforeAndAfterAll with StubControllerComponentsFactory with MockitoSugar {

  var container: GenericContainer = _
  var configuration: Configuration = _
  var esQueryService: ESQueryService = _

  override def beforeAll(): Unit = {
//    container = GenericContainer("docker.elastic.co/elasticsearch/elasticsearch:6.1.4",
//      exposedPorts = Seq(9200),
//      waitStrategy = Wait.forHttp("/"),
//      env = Map("discovery.type" -> "single-node", "cluster.name"->"elasticsearch")
//    )

//    container.start()

//    val elasticsearchClientUri = new ElasticsearchClientUri(
//      s"elasticsearch://${container.mappedPort(9200)}",
//      List(("localhost", container.mappedPort(9200))),
//      Map("ssl" -> "false")
//    )

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

//  override def afterAll(): Unit = {
//    container.close()
//  }

  private val IndexName = "member"


  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>  Json.toJson(t).toString()


  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  "GenericContainer" should "start ES and expose 9200 port" in {
    Source.fromInputStream(
      new URL(
        s"http://localhost:9200")
        .openConnection()
        .getInputStream)
      .mkString should include("\"number\" : \"6.1.4\"")
  }

  "ESQueryService" should "order documents by score" in {
      val result = esQueryService.generateFilterQueries(new QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.map(r => r.score).toSeq
    result shouldBe result.sorted(Ordering.Float.reverse)
  }

  it should "order documents by lastName for same score" in {
    val hitResultsByScore = esQueryService.generateFilterQueries(new QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.toSeq.groupBy(_.score).values.toSeq

    val searchHitLastNames =  hitResultsByScore.map(_.map(s => Json.parse(s.sourceAsString).as[MemberDocument].lastName))

    searchHitLastNames.foreach(l =>  l shouldBe l.sorted(Ordering.String))
  }
}
