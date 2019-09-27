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
      wildcardQuery("interests", s"*${qf.queryString}*"),
      matchPhrasePrefixQuery("firstName", s"${qf.queryString}"),
      matchPhrasePrefixQuery("lastName", s"${qf.queryString}").boost(2),
      matchPhrasePrefixQuery("institution", s"${qf.queryString}"),
      matchPhrasePrefixQuery("city", s"${qf.queryString}"),
      matchPhrasePrefixQuery("state", s"${qf.queryString}"),
      matchPhrasePrefixQuery("country", s"${qf.queryString}"),
      wildcardQuery("email", s"*${qf.queryString}*")
    )

    val resp = client.execute {
      search("member")
        .from(qf.start)
        .size(Math.abs(qf.end - qf.start)) //FIXME cannot be more that index.max_result_window
        .sortBy(FieldSortDefinition("_score"), FieldSortDefinition("lastName.keyword"))
        .bool{
          should(
            queriesShould
          )
        }
        .highlighting(
          highlight("interests"),
          highlight("firstName"),
          highlight("lastName"),
          highlight("institution"),
          highlight("city"),
          highlight("state"),
          highlight("country"),
          highlight("email"))
    }
    resp
  }
  sys.addShutdownHook(client.close())

}
