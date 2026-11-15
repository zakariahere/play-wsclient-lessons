package controllers

import jakarta.inject.{Inject, Singleton}
import models.{NewPost, Post}
import play.api.libs.json._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WsWriteController @Inject()(ws: WSClient,
                                  val controllerComponents: ControllerComponents)
                                 (implicit ec: ExecutionContext) extends BaseController {

  private val baseUrl = "https://jsonplaceholder.typicode.com"

  // Shared: "did we get the status we expected, and does the body decode to a Post?"
  private def toPostResult(response: WSResponse, expected: Int)(onSuccess: Post => Result): Result =
    if (response.status == expected)
      response.json.validate[Post] match {
        case JsSuccess(post, _) => onSuccess(post)
        case e: JsError =>
          BadGateway(Json.obj("error" -> "Upstream sent an unexpected shape",
            "details" -> JsError.toJson(e)))
      }
    else if (response.status == NOT_FOUND) NotFound(Json.obj("error" -> "No such post"))
    else BadGateway(Json.obj("error" -> s"Upstream said ${response.status}"))

  // 1) POST: create. Incoming JSON is validated by Play, outgoing JSON is written by us.
  def createPost(): Action[NewPost] = Action.async(parse.json[NewPost]) { request =>
    ws.url(s"$baseUrl/posts")
      .post(Json.toJson(request.body))
      .map(r => toPostResult(r, CREATED)(post => Created(Json.toJson(post))))
  }

  // 2) PUT: full replacement of an existing resource
  def replacePost(id: Int): Action[NewPost] = Action.async(parse.json[NewPost]) { request =>
    ws.url(s"$baseUrl/posts/$id")
      .put(Json.toJson(request.body))
      .map(r => toPostResult(r, OK)(post => Ok(Json.toJson(post))))
  }

  // 3) PATCH: send only what changes
  def patchTitle(id: Int): Action[JsValue] = Action.async(parse.json) { request =>
    (request.body \ "title").validate[String] match {
      case JsSuccess(title, _) =>
        ws.url(s"$baseUrl/posts/$id")
          .patch(Json.obj("title" -> title))
          .map(r => toPostResult(r, OK)(post => Ok(Json.toJson(post))))
      case e: JsError =>
        Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

  // 4) DELETE: no body going out, usually none coming back
  def deletePost(id: Int): Action[AnyContent] = Action.async {
    ws.url(s"$baseUrl/posts/$id").delete().map { r =>
      r.status match {
        case OK | NO_CONTENT => NoContent
        case NOT_FOUND       => NotFound(Json.obj("error" -> s"No post with id $id"))
        case other           => BadGateway(Json.obj("error" -> s"Upstream said $other"))
      }
    }
  }

  // 5) A form body instead of JSON (httpbin echoes back what it received)
  def formDemo(): Action[AnyContent] = Action.async {
    val form: Map[String, Seq[String]] = Map(
      "name"  -> Seq("Zakaria"),
      "langs" -> Seq("scala", "java")
    )
    ws.url("https://httpbin.org/post").post(form).map { r =>
      val contentTypeSent: String = (r.json \ "headers" \ "Content-Type").asOpt[String].getOrElse("?")
      val formReceived: JsValue   = (r.json \ "form").toOption.getOrElse(JsNull)

      Ok(Json.obj(
        "contentTypeSent" -> contentTypeSent,
        "formReceived"    -> formReceived
      ))
    }
  }
}