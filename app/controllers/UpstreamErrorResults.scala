package controllers

import models.UpstreamError
import models.UpstreamError._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._

object UpstreamErrorResults {

  def toResult(error: UpstreamError): Result = error match {
    case Missing(url)             => NotFound(Json.obj("error" -> "not_found", "upstream" -> url))
    case BadStatus(status, body)  => BadGateway(Json.obj("error" -> "bad_status", "upstreamStatus" -> status, "upstreamBody" -> body))
    case BadShape(details)        => BadGateway(Json.obj("error" -> "bad_shape", "details" -> details))
    case TimedOut(url)            => GatewayTimeout(Json.obj("error" -> "timeout", "upstream" -> url))
    case Unreachable(url, reason) => BadGateway(Json.obj("error" -> "unreachable", "upstream" -> url, "reason" -> reason))
  }
}
