package clients

import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

final case class UpstreamConfig(name: String, baseUrl: String, timeout: FiniteDuration, apiKey: Option[String]) {
  // Never print secrets: show just enough to recognise which key is loaded
  def maskedKey: Option[String] = apiKey.map(k => k.take(3) + "***")
}

object UpstreamConfig {

  // Throws at startup if base-url or timeout is missing or malformed: fail fast, not on the first request
  def load(config: Configuration, name: String): UpstreamConfig = {
    val c = config.get[Configuration](s"upstreams.$name")
    UpstreamConfig(
      name    = name,
      baseUrl = c.get[String]("base-url"),
      timeout = c.get[FiniteDuration]("timeout"),
      apiKey  = c.getOptional[String]("api-key")
    )
  }
}
