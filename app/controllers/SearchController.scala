package controllers

import javax.inject._
import models.{MemberDocument, QueryFilter}
import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.api.routing.sird._
import services.{AuthAction, ESQueryService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SearchController @Inject()(cc: ControllerComponents, esQueryService: ESQueryService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  def headers = List(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS, DELETE, PUT",
    "Access-Control-Max-Age" -> "3600",
    "Access-Control-Allow-Headers" -> "Origin, Content-Type, Accept, Authorization",
    "Access-Control-Allow-Credentials" -> "true"
  )

  def options = Action { request =>
    NoContent.withHeaders(headers : _*)
  }


  def search(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>

    type HighLights = Map[String, Map[String, Seq[String]]]

    val qs: QueryString = request.queryString

    object queryFilter extends QueryStringParameterExtractor[QueryFilter] {
      override def unapply(qs: QueryString): Option[QueryFilter] = qs match {
        case q"queryString=$queryString"  ?
          q"start=${int(start)}" ?
          q"end=${int(end)}" =>
          Some(QueryFilter(queryString, start, end))
        case _ =>
          None
      }
    }

    queryFilter.unapply(qs) match {
      case Some(qf) =>
        esQueryService.generateFilterQueries(qf) map  {
          case Right(reqSuccess) =>
            logger.info(s"ElasticSearch: RequestSuccesss with query parameters: ${qf.queryString} from ${qf.start} and size ${qf.end}")

            val publicMembers: Seq[(MemberDocument, HighLights)] = reqSuccess.result.hits.hits.map(sh => (
              Json.parse(sh.sourceAsString).as[MemberDocument],
              Map("highlight"-> sh.highlight)
            )).filter(_._1.isPublic).toSeq

            val result = new JsObject(
              Map(
                "totalMemberCount" -> Json.toJson(reqSuccess.result.totalHits),
                "publicMembers" -> Json.toJson(publicMembers)
              ))
            Ok(result)
          case Left(_) =>
            logger.error("ElasticSearch: RequestFailure was returned")
            BadRequest("ElasticSearch request failed")
        }
      case None => Future.successful(BadRequest("Invalid input query string"))
    }
  }

}