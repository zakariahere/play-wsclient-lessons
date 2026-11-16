package clients

import jakarta.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class HttpBinClient @Inject()(ws: WSClient, config: Configuration)(implicit ec: ExecutionContext)
  extends JsonHttp(ws, UpstreamConfig.load(config, "httpbin")) with HttpBinApi {

  def delay(seconds: Int): Upstream[JsValue] = getJson[JsValue](s"/delay/$seconds")

  // httpbin echoes back the headers it received: perfect to SEE what we send
  def headers(): Upstream[JsValue] = getJson[JsValue]("/headers")
}
