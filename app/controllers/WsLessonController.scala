package controllers

import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt


@Singleton
class WsLessonController @Inject()(ws: WSClient,
                                   val controllerComponents: ControllerComponents)(implicit ec: ExecutionContext) extends BaseController {


  private val baseUrl = "https://jsonplaceholder.typicode.com"


  def hello(): Action[AnyContent] = Action.async {
    val request: WSRequest = ws.url(s"$baseUrl/todos/1")
    request.get().map {
      response =>
        Ok(
          s"""Status: ${response.status} ${response.statusText}
             |Type: ${response.contentType}
             |
             |Body:
             |${response.body}""".stripMargin)
    }
  }


  def todo(id: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/todos/$id").addHttpHeaders("Accept" -> "application/json")
      .withRequestTimeout(5.seconds)
      .get().map {
        response =>
          response.status match {
            case OK => Ok(response.json)
            case NOT_FOUND => NotFound(s"No todo with id $id")
            case other => BadGateway(s"Upstream said $other : ${response.body}")
          }
      }
  }


  def todosForUser(userId: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/todos").addQueryStringParameters(
      "userId" -> userId.toString,
      "completed" -> "true"
    ).get().map(response => Ok(response.json))
  }
}
