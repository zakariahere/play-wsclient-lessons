package clients

import jakarta.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class HttpBinClient @Inject()(ws: WSClient)(implicit ec: ExecutionContext) extends JsonHttp(ws) {

  def delay(seconds: Int): Upstream[JsValue] = getJson[JsValue](s"https://httpbin.org/delay/$seconds")
}
