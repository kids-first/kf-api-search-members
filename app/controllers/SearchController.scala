package controllers

import javax.inject._
import models.{MemberDocument, QueryFilter}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc._
import play.api.routing.sird._
import services.{AuthAction, ESQueryService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SearchController @Inject()(cc: ControllerComponents, esQueryService: ESQueryService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  def search(): Action[AnyContent] = authAction.async { implicit request: Request[AnyContent] =>

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
            Ok(Json.toJson(reqSuccess.result.hits.hits.map(sh => (Json.parse(sh.sourceAsString).as[MemberDocument], Map("highlight"-> sh.highlight))).toSeq))
          case Left(_) =>
            logger.error("ElasticSearch: RequestFailure was returned")
            BadRequest("")
        }
      case None => Future.successful(BadRequest("Invalid input query string"))
    }
  }

}
