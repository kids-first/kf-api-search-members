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
          q"end=${int(end)}" =>
          Some(QueryFilter(queryString, start, end))
        case _ =>
          None
      }
    }

    queryFilter.unapply(qs) match {
      case Some(qf) =>
        val resultsF = esQueryService.generateFilterQueries(qf)
        val countsF = esQueryService.generateCountQueries(qf)

        val resultAndCount: Future[Either[RequestFailure, (RequestSuccess[SearchResponse], RequestSuccess[SearchResponse])]] = for {
          results <- resultsF
          counts <- countsF
        } yield {
          for {
            resultSuccess <- results
            countsSuccess <- counts
          } yield (resultSuccess, countsSuccess)
        }

        resultAndCount map {
          case Right((resultSuccess, countSuccess)) =>
            logger.info(s"ElasticSearch: RequestSuccess with query parameters: ${qf.queryString} from ${qf.start} and size ${qf.end}")
            val countAggs = countSuccess.result.aggregationsAsMap.asInstanceOf[Map[String, Map[String, Int]]]

            val publicMembers: Seq[JsObject] = resultSuccess.result.hits.hits.map(sh =>
              Json.parse(sh.sourceAsString).as[JsObject] ++
                Json.obj("_id" -> sh.id) ++
                Json.obj("highlight" -> sh.highlight)
            ).toSeq


            val result = Json.obj(
              "count" -> Json.obj(
                "total" -> countSuccess.result.totalHits,
                "public" -> fromCount("public", countAggs),
                "private" -> fromCount("private", countAggs)
              ),
              "publicMembers" -> Json.toJson(publicMembers)
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

  def fromCount(bucket: String, aggs: Map[String, Map[String, Int]]): Int = {
    aggs.get(bucket).flatMap(m => m.get("doc_count")).getOrElse(0)
  }
}