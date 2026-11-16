package clients

import jakarta.inject.{Inject, Singleton}
import models.{Post, Todo, UserSummary}
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class JsonPlaceholderClient @Inject()(ws: WSClient, config: Configuration)(implicit ec: ExecutionContext)
  extends JsonHttp(ws, UpstreamConfig.load(config, "jsonplaceholder")) with TodoApi {

  def todo(id: Int): Upstream[Todo]              = getJson[Todo](s"/todos/$id")
  def user(id: Int): Upstream[UserSummary]       = getJson[UserSummary](s"/users/$id")
  def todosFor(userId: Int): Upstream[Seq[Todo]] = getJson[Seq[Todo]](s"/todos?userId=$userId")
  def postsFor(userId: Int): Upstream[Seq[Post]] = getJson[Seq[Post]](s"/posts?userId=$userId")
}
