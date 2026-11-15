package clients

import jakarta.inject.{Inject, Singleton}
import models.{Post, Todo, UserSummary}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class JsonPlaceholderClient @Inject()(ws: WSClient)(implicit ec: ExecutionContext) extends JsonHttp(ws) {

  private val base = "https://jsonplaceholder.typicode.com"

  def todo(id: Int): Upstream[Todo]              = getJson[Todo](s"$base/todos/$id")
  def user(id: Int): Upstream[UserSummary]       = getJson[UserSummary](s"$base/users/$id")
  def todosFor(userId: Int): Upstream[Seq[Todo]] = getJson[Seq[Todo]](s"$base/todos?userId=$userId")
  def postsFor(userId: Int): Upstream[Seq[Post]] = getJson[Seq[Post]](s"$base/posts?userId=$userId")
}
