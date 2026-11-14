package controllers

import jakarta.inject.{Inject, Singleton}
import models.{Todo, UserSummary}
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import scala.concurrent.ExecutionContext

@Singleton
class WsJsonController @Inject()(ws: WSClient,
                                 val controllerComponents: ControllerComponents)(implicit ec: ExecutionContext)
  extends BaseController {

  private val baseUrl = "https://jsonplaceholder.typicode.com"

  // 1) The "YOLO" way: .as[T] throws if the JSON doesn't fit
  def todoAs(id: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/todos/$id").get().map { response =>
      val todo: Todo = response.json.as[Todo]
      Ok(Json.toJson(todo.copy(title = todo.title.toUpperCase)))
    }
  }

  // 2) The grown-up way: check status first, then validate the shape
  def todoValidate(id: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/todos/$id").get().map { response =>
      response.status match {
        case OK =>
          response.json.validate[Todo] match {
            case JsSuccess(todo, _) => Ok(Json.toJson(todo))
            case e: JsError =>
              BadGateway(Json.obj(
                "error"   -> "Upstream sent an unexpected shape",
                "details" -> JsError.toJson(e)))
          }
        case NOT_FOUND => NotFound(Json.obj("error" -> s"No todo with id $id"))
        case other     => BadGateway(Json.obj("error" -> s"Upstream said $other"))
      }
    }
  }

  // 3) Lists: validate a Seq and compute something with it
  def userStats(userId: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/todos")
      .addQueryStringParameters("userId" -> userId.toString)
      .get()
      .map { response =>
        response.json.validate[Seq[Todo]] match {
          case JsSuccess(todos, _) =>
            val done = todos.count(_.completed)
            Ok(Json.obj(
              "userId"    -> userId,
              "total"     -> todos.size,
              "completed" -> done,
              "pending"   -> (todos.size - done)))
          case e: JsError => BadGateway(JsError.toJson(e))
        }
      }
  }

  // 4) Custom Reads: reshape a nested upstream into our own model
  def userSummary(id: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/users/$id").get().map { response =>
      response.status match {
        case OK =>
          response.json.validate[UserSummary] match {
            case JsSuccess(user, _) => Ok(Json.toJson(user))
            case e: JsError         => BadGateway(JsError.toJson(e))
          }
        case NOT_FOUND => NotFound(Json.obj("error" -> s"No user with id $id"))
        case other     => BadGateway(Json.obj("error" -> s"Upstream said $other"))
      }
    }
  }

}
