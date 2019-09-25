package services

import java.net.URL

import com.dimafeng.testcontainers.GenericContainer
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.testkit.DockerTests
import com.sksamuel.elastic4s.{Index, Indexable}
import models.MemberDocument
import org.scalatest.FlatSpec
import org.scalatestplus.mockito.MockitoSugar
import org.testcontainers.containers.wait.strategy.Wait
import play.api.Configuration
import play.api.mvc.Results

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.Try

class ESQueryServiceSpec extends FlatSpec with MockitoSugar with DockerTests with Results {

  val container = GenericContainer("docker.elastic.co/elasticsearch/elasticsearch:6.1.4",
    exposedPorts = Seq(9200),
    waitStrategy = Wait.forHttp("/")
  )

  private val IndexName = "memberTest"

  val configuration: Configuration = Configuration.apply("elasticsearch.host"-> "localhost", "elasticsearch.ports" -> container.exposedPorts)

  val service = new ESQueryService(configuration)

  implicit val MemberIndexable: Indexable[MemberDocument] =
    (t: MemberDocument) => s"""{ "firstName" : "${t.firstName}", "lastName" : "${t.lastName}", "email" : "${t.email}", "isPublic" : "${t.isPublic}", "city" : "${t.city}", "state" : "${t.state}", "country" : "${t.country}" }"""

  Try {
    http.execute {
      deleteIndex(IndexName)
    }.await
  }

  http.execute {
    createIndex(IndexName).mappings(
      mapping(IndexName)
        .fields(
        textField("doc.firstName"),
        textField("doc.lastName"),
        textField("doc.email"),
        textField("doc.institutionalEmail"),
        booleanField("doc.acceptedTerms"),
        booleanField("doc.isPublic"),
//        booleanField("doc.Roles"), FIXME
        textField("doc.title"),
        textField("doc.jobTitle"),
        textField("doc.institution"),
        textField("doc.city"),
        textField("doc.state"),
        textField("doc.country"),
        textField("doc.eraCommonsID"),
        textField("doc.bio"),
        textField("doc.country"),
        textField("doc.story")
//        textField("doc.interests"), FIXME
//        textField("doc.virtualStudies") FIXME
      )
    )
  }.await

  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

//  client.execute(
//    bulk(
//      indexRequest("1", MemberDocument("Adrian", "PaulA", Some("adiemail@gmail.com"), isPublic = true, None, None, None, None, Nil)),
//      indexRequest("2", MemberDocument("Adrian", "PaulB", Some("adiemail@gmail.com"), isPublic = true, None, None, None, None, Nil))
//    ).refresh(RefreshPolicy.Immediate)
//  ).await

  "GenericContainer" should "start ES and expose 9200 port" in {
    assert(Source.fromInputStream(
      new URL(
        s"http://${container.containerIpAddress}:${container.mappedPort(9200)}/_status")
        .openConnection()
        .getInputStream)
      .mkString
      .contains("ES server is successfully installed"))
  }

}
