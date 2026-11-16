package controllers

import clients.{HttpBinClient, JsonPlaceholderClient, UpstreamConfig}
import controllers.UpstreamErrorResults.toResult
import jakarta.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class ConfigDemoController @Inject()(jp: JsonPlaceholderClient,
                                     bin: HttpBinClient,
                                     val controllerComponents: ControllerComponents)
                                    (implicit ec: ExecutionContext) extends BaseController {

  private def describe(c: UpstreamConfig): JsObject =
    Json.obj("baseUrl" -> c.baseUrl, "timeout" -> c.timeout.toString, "apiKey" -> c.maskedKey)

  // 1) What did we load from application.conf? (secrets masked)
  def settings(): Action[AnyContent] = Action {
    Ok(Json.obj("jsonplaceholder" -> describe(jp.upstream), "httpbin" -> describe(bin.upstream)))
  }

  // 2) What did the upstream actually RECEIVE? User-Agent, X-Api-Key, X-Correlation-Id
  def headers(): Action[AnyContent] = Action.async {
    bin.headers().map(_.fold(toResult, json => Ok(json)))
  }
}
