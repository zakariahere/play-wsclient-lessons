package controllers

import play.api.mvc._

import javax.inject.{Inject, Singleton}


@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def index(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok("Play up and running!")
  }
}
