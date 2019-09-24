package services

import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{HttpClient, RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import javax.inject.{Inject, Singleton}
import models.QueryFilter
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ESQueryService @Inject()(configuration: Configuration) {

  private val host = configuration.get[String]("elasticsearch.host")
  private val ports = configuration.get[Seq[Int]]("elasticsearch.ports")
  private val hosts_porst = ports.map(p => (host, p)).toList
  private val elasticsearchClientUri = new ElasticsearchClientUri(
    "elasticsearch://" + hosts_porst.map(h => s"${h._1}:${h._2}").mkString(","),
    hosts_porst
  )

  private val client = HttpClient(elasticsearchClientUri)

  def generateFilterQueries(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    import com.sksamuel.elastic4s.http.ElasticDsl._

    val queriesShould: Seq[QueryDefinition] = Seq(
      wildcardQuery("doc.interests", s"*${qf.queryString}*"),
      matchPhrasePrefixQuery("doc.firstName", s"${qf.queryString}"),
      matchPhrasePrefixQuery("doc.lastName", s"${qf.queryString}").boost(2),
      matchPhrasePrefixQuery("doc.institution", s"${qf.queryString}"),
      matchPhrasePrefixQuery("doc.city", s"${qf.queryString}"),
      matchPhrasePrefixQuery("doc.state", s"${qf.queryString}"),
      matchPhrasePrefixQuery("doc.country", s"${qf.queryString}"),
      wildcardQuery("doc.email", s"*${qf.queryString}*")
    )

    val resp = client.execute {
      search("member")
        .from(qf.start)
        .size(Math.abs(qf.end - qf.start)) //FIXME cannot be more that index.max_result_window
        .sortBy(FieldSortDefinition("_score"), FieldSortDefinition("doc.lastName.keyword"))
        .bool{
          should(
            queriesShould
          )
        }
        .highlighting(
          highlight("doc.interests"),
          highlight("doc.firstName"),
          highlight("doc.lastName"),
          highlight("doc.institution"),
          highlight("doc.city"),
          highlight("doc.state"),
          highlight("doc.country"),
          highlight("doc.email"))
    }
    resp
  }
  sys.addShutdownHook(client.close())

}
