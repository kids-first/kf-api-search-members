package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class StatusController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def index(): Action[AnyContent] = Action {
    Ok("Ok!")
  }


}