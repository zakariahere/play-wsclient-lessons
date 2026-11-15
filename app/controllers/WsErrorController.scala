package controllers

import cats.data.EitherT
import jakarta.inject.{Inject, Singleton}
import models.UpstreamError._
import models.{Todo, UpstreamError, UserSummary}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._

import java.net.{ConnectException, UnknownHostException}
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class WsErrorController @Inject()(ws: WSClient,
                                  val controllerComponents: ControllerComponents)
                                 (implicit ec: ExecutionContext) extends BaseController {

  private val baseUrl = "https://jsonplaceholder.typicode.com"

  // ─── The one helper: EVERY outcome becomes an Either, the Future never fails ───
  private def getJson[A: Reads](url: String,
                                timeout: FiniteDuration = 5.seconds): Future[Either[UpstreamError, A]] =
    ws.url(url)
      .withRequestTimeout(timeout)
      .get()
      .map[Either[UpstreamError, A]] { r =>        // server answered: inspect status + shape
        r.status match {
          case OK =>
            r.json.validate[A] match {
              case JsSuccess(a, _) => Right(a)
              case e: JsError      => Left(BadShape(JsError.toJson(e)))
            }
          case NOT_FOUND => Left(Missing(url))
          case other     => Left(BadStatus(other, r.body.take(200)))
        }
      }
      .recover {                                   // no answer: turn the exception into a value
        case _: TimeoutException     => Left(TimedOut(url))
        case e: ConnectException     => Left(Unreachable(url, s"connect: ${e.getMessage}"))
        case e: UnknownHostException => Left(Unreachable(url, s"dns: ${e.getMessage}"))
        case NonFatal(e)             => Left(Unreachable(url, s"${e.getClass.getSimpleName}: ${e.getMessage}"))
      }

  // ─── The one translation: UpstreamError -> HTTP Result ───
  private def toResult(error: UpstreamError): Result = error match {
    case Missing(url)             => NotFound(Json.obj("error" -> "not_found", "upstream" -> url))
    case BadStatus(status, body)  => BadGateway(Json.obj("error" -> "bad_status", "upstreamStatus" -> status, "upstreamBody" -> body))
    case BadShape(details)        => BadGateway(Json.obj("error" -> "bad_shape", "details" -> details))
    case TimedOut(url)            => GatewayTimeout(Json.obj("error" -> "timeout", "upstream" -> url))
    case Unreachable(url, reason) => BadGateway(Json.obj("error" -> "unreachable", "upstream" -> url, "reason" -> reason))
  }

  // 1) Happy path, 404 and shape errors, now one-liners
  def todo(id: Int): Action[AnyContent] = Action.async {
    getJson[Todo](s"$baseUrl/todos/$id").map(_.fold(toResult, t => Ok(Json.toJson(t))))
  }

  // 2) Timeout: httpbin waits 5s, we only allow 2s
  def slow(): Action[AnyContent] = Action.async {
    getJson[JsValue]("https://httpbin.org/delay/5", timeout = 2.seconds).map(_.fold(toResult, Ok(_)))
  }

  // 3) DNS failure: the .invalid TLD is reserved and never resolves
  def noHost(): Action[AnyContent] = Action.async {
    getJson[JsValue]("https://does-not-exist.invalid/").map(_.fold(toResult, Ok(_)))
  }

  // 4) Connection refused: nothing listens on port 1
  def refused(): Action[AnyContent] = Action.async {
    getJson[JsValue]("http://localhost:1/").map(_.fold(toResult, Ok(_)))
  }

  // 5) Upstream answers with any status you like
  def status(code: Int): Action[AnyContent] = Action.async {
    getJson[JsValue](s"https://httpbin.org/status/$code").map(_.fold(toResult, Ok(_)))
  }

  // 6) Wrong shape: decode a USER as a Todo
  def shape(): Action[AnyContent] = Action.async {
    getJson[Todo](s"$baseUrl/users/1").map(_.fold(toResult, t => Ok(Json.toJson(t))))
  }

  // 7) recoverWith: on timeout, fall back to ANOTHER async call
  def fallback(): Action[AnyContent] = Action.async {
    ws.url("https://httpbin.org/delay/5").withRequestTimeout(2.seconds).get()
      .recoverWith { case _: TimeoutException => ws.url(s"$baseUrl/todos/1").get() }
      .map(r => Ok(Json.obj("servedBy" -> r.uri.getHost, "body" -> r.json)))
  }

  // 8) Chaining two calls that can each fail: EitherT (cats is already in your build.sbt!)
  def dashboard(id: Int): Action[AnyContent] = Action.async {
    val result = for {
      user  <- EitherT(getJson[UserSummary](s"$baseUrl/users/$id"))
      todos <- EitherT(getJson[Seq[Todo]](s"$baseUrl/todos?userId=$id"))
    } yield Json.obj("user" -> user, "openTodos" -> todos.count(!_.completed))

    result.value.map(_.fold(toResult, Ok(_)))
  }
}