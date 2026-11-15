package clients

import models.UpstreamError
import models.UpstreamError._
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.WSClient

import java.net.{ConnectException, UnknownHostException}
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

abstract class JsonHttp(ws: WSClient)(implicit ec: ExecutionContext) {

  protected def getJson[A: Reads](url: String, timeout: FiniteDuration = 5.seconds): Upstream[A] =
    ws.url(url)
      .withRequestTimeout(timeout)
      .get()
      .map[Either[UpstreamError, A]] { r =>
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
      .recover {
        case _: TimeoutException     => Left(TimedOut(url))
        case e: ConnectException     => Left(Unreachable(url, s"connect: ${e.getMessage}"))
        case e: UnknownHostException => Left(Unreachable(url, s"dns: ${e.getMessage}"))
        case NonFatal(e)             => Left(Unreachable(url, s"${e.getClass.getSimpleName}: ${e.getMessage}"))
      }
}
