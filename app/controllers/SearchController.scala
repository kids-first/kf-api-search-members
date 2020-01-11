package controllers

import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{RequestFailure, RequestSuccess}
import controllers.SearchController._
import javax.inject._
import models.QueryFilter
import play.api.Logging
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import play.api.routing.sird._
import services.{AuthAction, ESQueryService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SearchController @Inject()(cc: ControllerComponents, esQueryService: ESQueryService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  implicit val bucketlistWrites: Writes[List[(String, Int)]] = (list: List[(String, Int)]) => Json.obj(list.map { case (s, o) =>
    val ret: (String, JsValueWrapper) = s.toString -> JsNumber(o)
    ret
  }: _*)

  def search(): Action[AnyContent] = authAction.async { implicit request: Request[AnyContent] =>

    val qs: QueryString = request.queryString


    object queryFilter extends QueryStringParameterExtractor[QueryFilter] {
      override def unapply(qs: QueryString): Option[QueryFilter] = qs match {
        case q"queryString=$queryString" ?
          q"start=${int(start)}" ?
          q"end=${int(end)}" ?
          q_s"role=$roles" ?
          q_s"interest=$interests" =>
          Some(QueryFilter(queryString, start, end, roles, interests))
        case _ =>
          None
      }
    }

    queryFilter.unapply(qs) match {
      case Some(qf) =>
        val resultsF = esQueryService.generateFilterQueries(qf)
        val rolesAggsF = esQueryService.generateRolesAggQuery(qf)
        val interestsAggsF = esQueryService.generateInterestsAggQuery(qf)
        val countsF = esQueryService.generateCountQueries(qf)

        val all: Future[Either[RequestFailure, (RequestSuccess[SearchResponse], RequestSuccess[SearchResponse], RequestSuccess[SearchResponse], RequestSuccess[SearchResponse])]] = for {
          results <- resultsF
          counts <- countsF
          rolesAggs <- rolesAggsF
          interestsAggs <- interestsAggsF
        } yield {
          for {
            resultSuccess <- results
            countsSuccess <- counts
            rolesAggsSuccess <- rolesAggs
            interestsAggsSuccess <- interestsAggs
          } yield (resultSuccess, countsSuccess, rolesAggsSuccess, interestsAggsSuccess)
        }

        all map {
          case Right((resultSuccess, countSuccess, rolesAggsSuccess, interestsAggsSuccess)) =>
            logger.info(s"ElasticSearch: RequestSuccess with query parameters: q=${qf.queryString} roles=${} from ${qf.start} and size ${qf.end}")
            val countAggs = countSuccess.result.aggregationsAsMap.asInstanceOf[Map[String, Map[String, Int]]]
            val rolesAggs = rolesAggsSuccess.result.aggregationsAsMap

            val (interests, interestsOthers) = getBuckets("interests", interestsAggsSuccess.result.aggregationsAsMap)

            val publicMembers: Seq[JsObject] = resultSuccess.result.hits.hits.map(sh =>
              Json.parse(sh.sourceAsString).as[JsObject] ++
                Json.obj("_id" -> sh.id) ++
                Json.obj("highlight" -> Option(sh.highlight))
            ).toSeq

            val result = Json.obj(
              "count" -> Json.obj(
                "total" -> countSuccess.result.totalHits,
                "public" -> fromCount("public", countAggs),
                "private" -> fromCount("private", countAggs),
                "interests" -> Json.toJson(interests),
                "interestsOthers" -> interestsOthers,
                "roles" -> Json.obj(
                  "research" -> fromCount("research", rolesAggs),
                  "health" -> fromCount("health", rolesAggs),
                  "patient" -> fromCount("patient", rolesAggs),
                  "community" -> fromCount("community", rolesAggs),
                )
              ),
              "publicMembers" -> Json.toJson(publicMembers),
            )
            Ok(result)
          case Left(failure) =>
            logger.error(s"ElasticSearch: RequestFailure was returned $failure")
            InternalServerError(s"ElasticSearch request failed $failure")
        }
      case None => Future.successful(BadRequest("Invalid input query string"))
    }
  }

  def interests(): Action[AnyContent] = authAction.async { implicit request: Request[AnyContent] => //FIXME  CHANGE THIS BACK
    request.queryString match {
      case q"queryString=$queryString" =>
        esQueryService.generateInterestsQuery(queryString) map {
          case Right(respSucces) =>

            //Drill down to "buckets"
            val buckets = respSucces.result.aggregationsAsMap
              .get("all").asInstanceOf[Option[Agg]]
              .flatMap(a =>
                a.get("filtered").asInstanceOf[Option[Agg]])
              .flatMap(ri =>
                ri.get("searchableInterests").asInstanceOf[Option[Agg]])
              .flatMap(r =>
                r.get("buckets").asInstanceOf[Option[List[Agg]]])

            val interests = buckets match {
              case Some(list) => list.map(i => i.getOrElse("key", "")).asInstanceOf[List[String]].filter(_ != "")
              case None => List.empty
            }

            Ok(Json.obj("interests" -> interests))

          case Left(failure) =>
            logger.error(s"ElasticSearch: RequestFailure was returned $failure")
            InternalServerError(s"ElasticSearch request failed $failure")
        }

      case _ => Future.successful(BadRequest("Invalid input query string"))
    }
  }

  def interestsStats(): Action[AnyContent] = authAction.async { implicit request: Request[AnyContent] => //FIXME  CHANGE THIS BACK
    request.queryString match {
      case q_o"size=${int(size)}" =>
        esQueryService.generateInterestsStatsQuery(size) map {
          case Right(respSucces) =>

            //Drill down to "buckets"
            val all = respSucces.result.aggregationsAsMap
              .get("all").asInstanceOf[Option[Agg]]

            val searchableInterst = all
              .flatMap(ri =>
                ri.get("searchableInterests").asInstanceOf[Option[Agg]])

            val buckets = searchableInterst.flatMap(r =>
              r.get("buckets").asInstanceOf[Option[List[Agg]]])

            val interests: Seq[JsObject] = buckets match {
              case Some(list) =>
                list.map(r => Json.obj("name" -> r("key").asInstanceOf[String], "count" -> r("doc_count").asInstanceOf[Int]))
              case None => Seq.empty
            }

            val others: Option[Int] = searchableInterst.map(s => s("sum_other_doc_count").asInstanceOf[Int])
            val results: Seq[JsObject] = others match {
              case Some(c) if c > 0 => interests :+ Json.obj("name" -> "Others", "count" -> c)
              case _ => interests
            }

            Ok(Json.obj("interests" -> results))

          case Left(failure) =>
            logger.error(s"ElasticSearch: RequestFailure was returned $failure")
            InternalServerError(s"ElasticSearch request failed $failure")
        }

      case _ => Future.successful(BadRequest("Invalid input query string"))
    }
  }
}

object SearchController {

  type Agg = Map[String, Any]
  type HighLights = Map[String, Map[String, Seq[String]]]

  def fromCount(bucket: String, aggs: Agg): Int = {
    val buckets = aggs.get(bucket).asInstanceOf[Option[Map[String, Int]]]
    buckets.flatMap(m => m.get("doc_count")).getOrElse(0)
  }

  def getBuckets(bucketName: String, agg: Agg): (List[(String, Int)], Int) = {
    val bucket = agg.get(bucketName).asInstanceOf[Option[Agg]]
    val allBuckets = bucket.flatMap(i => i.get("buckets")).asInstanceOf[Option[List[Agg]]]
    val countOthers = bucket.flatMap(i => i.get("sum_other_doc_count")).asInstanceOf[Option[Int]]

    allBuckets match {
      case Some(list) => (list.map(i => (i.getOrElse("key", ""), i.getOrElse("doc_count", 0))).asInstanceOf[List[(String, Int)]].filter(_._1 != ""), countOthers.getOrElse(0))
      case None => (List.empty, 0)
    }
  }
}