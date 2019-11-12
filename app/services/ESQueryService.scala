package services

import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{HttpClient, RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.searches.queries.matches.MatchQueryDefinition
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, QueryDefinition}
import com.sksamuel.elastic4s.searches.sort.{FieldSortDefinition, SortOrder}
import com.sksamuel.exts.Logging
import javax.inject.{Inject, Singleton}
import models.QueryFilter
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ESQueryService @Inject()(configuration: Configuration) extends Logging {

  private val host = configuration.get[String]("elasticsearch.host")
  private val ports = configuration.get[Seq[Int]]("elasticsearch.ports")
  private val hosts_ports = ports.map(p => (host, p)).toList
  private val elasticsearchClientUri = new ElasticsearchClientUri(
    s"elasticsearch://" + hosts_ports.map(h => s"${h._1}:${h._2}").mkString(","),
    hosts_ports
  )

  private val client = HttpClient(elasticsearchClientUri)

  def generateCountQueries(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .bool {
        queryFilter(qf, matchQuery("acceptedTerms", true))
      }
      .aggregations(
        filterAgg("public", termQuery("isPublic", true)),
        filterAgg("private", termQuery("isPublic", false))
      )
    logger.debug(s"ES Query = ${client.show(q)}")
    client.execute {
      q
    }

  }

  def generateRoleCountQueries(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .bool {
        BoolQueryDefinition().filter(matchQuery("acceptedTerms", true), matchQuery("isPublic", true)).should(
          matchQueryString(qf)
        ).minimumShouldMatch(1)
      }
      .aggregations(
        filterAgg("research", termQuery("roles", "research")),
        filterAgg("community", termQuery("roles", "community")),
        filterAgg("patient", termQuery("roles", "patient")),
        filterAgg("health", termQuery("roles", "health")),
        termsAgg("interests", "interests.raw")
      )
    logger.debug(s"ES Query = ${client.show(q)}")
    client.execute {
      q
    }

  }

  def generateFilterQueries(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {

    val q = search("member")
      .from(qf.start)
      .size(Math.abs(qf.end - qf.start)) //FIXME cannot be more that index.max_result_window
      .sortBy(FieldSortDefinition("_score", order = SortOrder.Desc), FieldSortDefinition("lastName.raw"))
      .sourceInclude("firstName", "lastName", "email", "roles", "title", "institution", "city", "state", "country", "interests")
      .bool {
        queryFilter(qf, matchQuery("acceptedTerms", true), matchQuery("isPublic", true))
      }

    val highlightedQuery = if (qf.queryString.isEmpty) q else
      q.highlighting(
        highlight("interests"),
        highlight("firstName"),
        highlight("lastName"),
        highlight("institution"),
        highlight("city"),
        highlight("state"),
        highlight("country"),
        highlight("email"))
    logger.warn(s"ES Query = ${client.show(highlightedQuery)}")
    val resp = client.execute {
      highlightedQuery
    }
    resp
  }

  private def matchQueryString(qf: QueryFilter) = {
    Seq(
      wildcardQuery("interests", s"*${qf.queryString}*"),
      matchPhrasePrefixQuery("firstName", s"${qf.queryString}"),
      matchPhrasePrefixQuery("lastName", s"${qf.queryString}").boost(2),
      matchPhrasePrefixQuery("institution", s"${qf.queryString}"),
      matchPhrasePrefixQuery("city", s"${qf.queryString}"),
      matchPhrasePrefixQuery("state", s"${qf.queryString}"),
      matchPhrasePrefixQuery("country", s"${qf.queryString}"),
      wildcardQuery("email", s"*${qf.queryString}*")
    )
  }

  private def queryFilter(qf: QueryFilter, filters: MatchQueryDefinition*) = {

    val appendedFiltersRoles = if (qf.roles.nonEmpty) filters :+ should(qf.roles.map(r => matchQuery("roles", r))).minimumShouldMatch(1) else filters
    val appendedFiltersInterests = if (qf.interests.nonEmpty) appendedFiltersRoles :+ should(qf.interests.map(i => matchQuery("interests.raw", i))).minimumShouldMatch(1) else appendedFiltersRoles

    val queriesShould: Seq[QueryDefinition] = matchQueryString(qf)
    BoolQueryDefinition()
      .filter(appendedFiltersInterests)
      .should(queriesShould)
      .minimumShouldMatch(1)
  }

  sys.addShutdownHook(client.close())

}
