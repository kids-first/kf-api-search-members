package services

import java.net.URL

import com.dimafeng.testcontainers.GenericContainer
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.testkit.DockerTests
import com.sksamuel.elastic4s.{ElasticsearchClientUri, Index, Indexable, RefreshPolicy}
import com.sksamuel.elastic4s.mappings.FieldType._
import controllers.SearchController
import models.{MemberDocument, QueryFilter}
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import org.testcontainers.containers.wait.strategy.Wait
import pdi.jwt.JwtClaim
import play.api.Configuration
import play.api.mvc.BodyParsers
import play.api.test.StubControllerComponentsFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Failure, Success, Try}

class ESQueryServiceSpec extends FlatSpec with DockerTests with Matchers with BeforeAndAfterAll with StubControllerComponentsFactory with MockitoSugar {

  var container: GenericContainer = _
  var configuration: Configuration = _
  var esQueryService: ESQueryService = _

  override def beforeAll(): Unit = {
    container = GenericContainer("docker.elastic.co/elasticsearch/elasticsearch:6.1.4",
      exposedPorts = Seq(9200),
      waitStrategy = Wait.forHttp("/"),
      env = Map("discovery.type" -> "single-node", "cluster.name"->"elasticsearch")
    )

    container.start()

    val elasticsearchClientUri = new ElasticsearchClientUri(
      s"elasticsearch://${container.mappedPort(9200)}",
      List(("localhost", container.mappedPort(9200))),
      Map("ssl" -> "false")
    )

    val dockerclient = HttpClient(elasticsearchClientUri)

    Try {
      dockerclient.execute {
        deleteIndex(IndexName)
      }.await

//      dockerclient.execute {
//        createIndex(IndexName).mappings(
//          mapping(IndexName)
//            .fields(
//              textField("firstName"),
//              keywordField("lastName"),
//              textField("email"),
//              textField("institutionalEmail"),
//              booleanField("acceptedTerms"),
//              booleanField("isPublic"),
//              //        textField("doc.Roles"), FIXME
//              textField("title"),
//              textField("jobTitle"),
//              textField("institution"),
//              textField("city"),
//              textField("state"),
//              textField("country"),
//              textField("eraCommonsID"),
//              textField("bio"),
//              textField("country"),
//              textField("story")
//              //        textField("doc.interests"), FIXME
//              //        textField("doc.virtualStudies") FIXME
//            )
//        )
//      }.await
      dockerclient.execute {
        createIndex(IndexName).mappings(
          mapping(IndexName)
            .fields(
              textField("firstName"),
              keywordField("lastName"),
              textField("email"),
              textField("institutionalEmail"),
              booleanField("acceptedTerms"),
              booleanField("isPublic"),
              //        textField("doc.Roles"), FIXME
              textField("title"),
              textField("jobTitle"),
              textField("institution"),
              textField("city"),
              textField("state"),
              textField("country"),
              textField("eraCommonsID"),
              textField("bio"),
              textField("country"),
              textField("story")
              //        textField("doc.interests"), FIXME
              //        textField("doc.virtualStudies") FIXME
            )
        )
      }.await

      dockerclient.execute(
        bulk(
          indexRequest("1", MemberDocument("Adrian", "PaulA", Some("adiemail@gmail.com"), isPublic = true, None, None, None, None, Nil)),
          indexRequest("2", MemberDocument("Adrian", "PaulB", Some("adiemail@gmail.com"), isPublic = true, None, None, None, None, Nil))
        ).refresh(RefreshPolicy.Immediate)
      ).await
    } match {
      case Success(value) => println("SUCCESS")
      case Failure(e) => println(e.toString)
    }
    configuration = Configuration.apply("elasticsearch.host"-> "localhost", "elasticsearch.ports" -> List(container.mappedPort(9200)))

    esQueryService = new ESQueryService(configuration)
  }

  override def afterAll(): Unit = {
    container.close()
  }

  private val IndexName = "member"


  implicit val MemberIndexable: Indexable[MemberDocument] =
    (t: MemberDocument) => s"""{ "firstName" : "${t.firstName}", "lastName" : "${t.lastName}", "email" : "${t.email}", "isPublic" : "${t.isPublic}", "city" : "${t.city}", "state" : "${t.state}", "country" : "${t.country}" }"""


  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  "GenericContainer" should "start ES and expose 9200 port" in {
    Source.fromInputStream(
      new URL(
        s"http://${container.containerIpAddress}:${container.mappedPort(9200)}")
        .openConnection()
        .getInputStream)
      .mkString should include("\"number\" : \"6.1.4\"")
  }

  "ESQueryServiceSpec" should "order documents by score" in {
    println(Source.fromInputStream(
      new URL(
        s"http://${container.containerIpAddress}:${container.mappedPort(9200)}/${IndexName}/_search?q=*")
        .openConnection()
        .getInputStream).mkString
    )
//    println(esQueryService.generateFilterQueries(new QueryFilter("Adrian", 1, 10)).await)
    "one" shouldBe("one")
  }
}