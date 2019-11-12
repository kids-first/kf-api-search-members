package controllers

import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{RequestFailure, RequestSuccess}
import controllers.SearchController._
import javax.inject._
import models.QueryFilter
import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.api.routing.sird._
import services.{AuthAction, ESQueryService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SearchController @Inject()(cc: ControllerComponents, esQueryService: ESQueryService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  def search(): Action[AnyContent] = authAction.async { implicit request: Request[AnyContent] =>

    type HighLights = Map[String, Map[String, Seq[String]]]

    val qs: QueryString = request.queryString

    object queryFilter extends QueryStringParameterExtractor[QueryFilter] {
      override def unapply(qs: QueryString): Option[QueryFilter] = qs match {
        case q"queryString=$queryString" ?
          q"start=${int(start)}" ?
          q"end=${int(end)}" ?
          q_s"role=$roles" ?
          q_s"interests=$interests"=>
          Some(QueryFilter(queryString, start, end, roles, interests))
        case _ =>
          None
      }
    }

    queryFilter.unapply(qs) match {
      case Some(qf) =>
        val resultsF = esQueryService.generateFilterQueries(qf)
        val countsF = esQueryService.generateCountQueries(qf)
        val countsRolesF = esQueryService.generateRoleCountQueries(qf)

        val resultAndCount: Future[Either[RequestFailure, (RequestSuccess[SearchResponse], RequestSuccess[SearchResponse], RequestSuccess[SearchResponse])]] = for {
          results <- resultsF
          counts <- countsF
          countsRoles <- countsRolesF
        } yield {
          for {
            resultSuccess <- results
            countsSuccess <- counts
            countsRolesSuccess <- countsRoles
          } yield (resultSuccess, countsSuccess, countsRolesSuccess)
        }

        resultAndCount map {
          case Right((resultSuccess, countSuccess, countsRolesSuccess)) =>
            logger.info(s"ElasticSearch: RequestSuccess with query parameters: q=${qf.queryString} roles=${} from ${qf.start} and size ${qf.end}")
            val countAggs = countSuccess.result.aggregationsAsMap.asInstanceOf[Map[String, Map[String, Int]]]
            val countAggsRoles = countsRolesSuccess.result.aggregationsAsMap

            //FIXME can asInstanceOf be avoided?
            val interests = countAggsRoles.get("interests").asInstanceOf[Option[Map[String, Any]]]
            val buckets = interests.flatMap(i => i.get("buckets")).asInstanceOf[Option[List[Map[String,Any]]]]

            //FIXME this works, but its horrible... to be discussed
            val listOfInterests = buckets match {
              case Some(list) => list.map(i => (i.getOrElse("key", ""), i.getOrElse("doc_count", 0))).asInstanceOf[List[(String, Int)]].filter(_._1 != "")
              case None => List.empty
            }

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
                "research" -> fromCount("research", countAggsRoles),
                "community" -> fromCount("community", countAggsRoles),
                "patient" -> fromCount("patient", countAggsRoles),
                "health" -> fromCount("health", countAggsRoles),
              ),
              "publicMembers" -> Json.toJson(publicMembers),
              "interests" -> listOfInterests.map(i => Json.obj(i._1.toString -> i._2))
            )
            Ok(result)
          case Left(failure) =>
            logger.error(s"ElasticSearch: RequestFailure was returned $failure")
            InternalServerError(s"ElasticSearch request failed $failure")
        }
      case None => Future.successful(BadRequest("Invalid input query string"))
    }
  }


}

object SearchController {

  def fromCount(bucket: String, aggs: Map[String, Any]): Int = {
    val buckets = aggs.get(bucket).asInstanceOf[Option[Map[String, Int]]]
    buckets.flatMap(m => m.get("doc_count")).getOrElse(0)
  }
}