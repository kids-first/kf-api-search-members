package services

import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.analyzers.StandardAnalyzer
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{HttpClient, RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.searches.aggs.TermsAggregationDefinition
import com.sksamuel.elastic4s.searches.queries.matches.{MatchQueryDefinition, ZeroTermsQuery}
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
  private val MAX_INTERESTS_STATS = 100
  private val host = configuration.get[String]("elasticsearch.host")
  private val ports = configuration.get[Seq[Int]]("elasticsearch.ports")
  private val ssl = configuration.get[String]("elasticsearch.ssl")
  private val hosts_ports = ports.map(p => (host, p)).toList
  private val elasticsearchClientUri = new ElasticsearchClientUri(
    s"elasticsearch://" + hosts_ports.map(h => s"${h._1}:${h._2}").mkString(","),
    hosts_ports,
    Map("ssl" -> ssl)
  )

  private val client = HttpClient(elasticsearchClientUri)

  private val qfSelect = (qf: QueryFilter) => if (qf.qAllMembers) {
    queryFilter(qf, matchQuery("acceptedTerms", true))
  } else {
    queryFilter(
      qf,
      matchQuery("acceptedTerms", true),
      matchQuery("isPublic", true),
      matchQuery("isActive", true))
  }


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

  def generateRolesAggQuery(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .bool {
        qfSelect(qf.copy(roles = Nil))
      }
      .aggregations(
        filterAgg("research", termQuery("roles", "research")),
        filterAgg("community", termQuery("roles", "community")),
        filterAgg("patient", termQuery("roles", "patient")),
        filterAgg("health", termQuery("roles", "health"))
      )
    logger.debug(s"ES Query = ${client.show(q)}")
    client.execute(q)

  }

  def generateInterestsAggQuery(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .bool {
        qfSelect(qf.copy(interests = Nil))
      }
      .aggregations(
        TermsAggregationDefinition("interests", size = Some(1000)).field("interests.raw")
      )
    logger.debug(s"ES Query = ${client.show(q)}")
    client.execute(q)

  }

  def generateFilterQueries(qf: QueryFilter): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {

    val q = search("member")
      .from(qf.start)
      .size(Math.abs(qf.end - qf.start)) //FIXME cannot be more that index.max_result_window
      .sortBy(FieldSortDefinition("_score", order = SortOrder.Desc), FieldSortDefinition("lastName.raw"))
      .sourceInclude("firstName", "lastName", "hashedEmail", "roles", "title", "institution", "city", "state", "country", "interests", "isPublic", "isActive")
      .bool {
        qfSelect(qf)
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
        highlight("bio"),
        highlight("story"))
    logger.debug(s"ES Query = ${client.show(highlightedQuery)}")
    val resp = client.execute {
      highlightedQuery
    }
    resp
  }

  def generateInterestsQuery(qs: String): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .aggregations(
        nestedAggregation("all", "searchableInterests")
          .subAggregations(
            filterAgg("filtered", boolQuery().should(
              matchQuery("searchableInterests.name", qs)
                .zeroTermsQuery("all")
                .analyzer(StandardAnalyzer)
                .operator("and")
            ))
              .subAggregations(
                TermsAggregationDefinition(name = "searchableInterests", field = Some("searchableInterests.name.raw"), size = Some(10))
              )
          )
      )

    logger.debug(s"ES QueryINT = ${client.show(q)}")

    client.execute(q)

  }

  def generateInterestsStatsQuery(size: Option[Int]): Future[Either[RequestFailure, RequestSuccess[SearchResponse]]] = {
    val q = search("member")
      .size(0)
      .aggregations(
        nestedAggregation("all", "searchableInterests")
          .subAggregations(
            TermsAggregationDefinition(name = "searchableInterests", field = Some("searchableInterests.name.raw"), size = size.orElse(Some(MAX_INTERESTS_STATS)))
          )
      )

    logger.debug(s"ES QueryINT = ${client.show(q)}")

    client.execute(q)

  }

  private def matchQueryString(qf: QueryFilter) = {
    Seq(
      multiMatchQuery(qf.queryString)
        .zeroTermsQuery(ZeroTermsQuery.ALL)
        .analyzer(StandardAnalyzer)
        .fields("firstName",
          "lastName^5",
          "interests",
          "institution",
          "city",
          "state",
          "country",
          "bio",
          "story")
    )
  }

  private def queryFilter(qf: QueryFilter, filters: MatchQueryDefinition* ) = {

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
