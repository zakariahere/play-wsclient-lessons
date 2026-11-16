package clients

import models.UpstreamError
import models.UpstreamError._
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}

import java.net.{ConnectException, UnknownHostException}
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

abstract class JsonHttp(ws: WSClient, val upstream: UpstreamConfig)(implicit ec: ExecutionContext) {

  private val logging = new LoggingFilter(upstream.name)

  // Every request of this client goes through here: base URL, timeout, auth header, logging filter
  protected def request(path: String): WSRequest = {
    val base = ws.url(upstream.baseUrl + path)
      .withRequestTimeout(upstream.timeout)
      .withRequestFilter(logging)

    upstream.apiKey.fold(base)(key => base.addHttpHeaders("X-Api-Key" -> key))
  }

  protected def getJson[A: Reads](path: String): Upstream[A] = {
    val req = request(path)
    req.get()
      .map[Either[UpstreamError, A]] { r =>
        r.status match {
          case OK =>
            r.json.validate[A] match {
              case JsSuccess(a, _) => Right(a)
              case e: JsError      => Left(BadShape(JsError.toJson(e)))
            }
          case NOT_FOUND => Left(Missing(req.url))
          case other     => Left(BadStatus(other, r.body.take(200)))
        }
      }
      .recover {
        case _: TimeoutException     => Left(TimedOut(req.url))
        case e: ConnectException     => Left(Unreachable(req.url, s"connect: ${e.getMessage}"))
        case e: UnknownHostException => Left(Unreachable(req.url, s"dns: ${e.getMessage}"))
        case NonFatal(e)             => Left(Unreachable(req.url, s"${e.getClass.getSimpleName}: ${e.getMessage}"))
      }
  }
}
