package models

import play.api.libs.json.JsObject

sealed trait UpstreamError extends Product with Serializable

object UpstreamError {
  final case class Missing(url: String) extends UpstreamError
  final case class BadStatus(status: Int, body: String) extends UpstreamError
  final case class BadShape(details: JsObject) extends UpstreamError
  final case class TimedOut(url: String) extends UpstreamError
  final case class Unreachable(url: String, reason: String) extends UpstreamError
}

