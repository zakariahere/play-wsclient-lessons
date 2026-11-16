package clients

import com.google.inject.ImplementedBy
import models.{Post, Todo, UserSummary}
import play.api.libs.json.JsValue

// Lesson 7: controllers depend on these traits, not on the HTTP classes.
// Production: Guice picks the @ImplementedBy class. Tests: pass a hand-written fake.

@ImplementedBy(classOf[JsonPlaceholderClient])
trait TodoApi {
  def todo(id: Int): Upstream[Todo]
  def user(id: Int): Upstream[UserSummary]
  def todosFor(userId: Int): Upstream[Seq[Todo]]
  def postsFor(userId: Int): Upstream[Seq[Post]]
}

@ImplementedBy(classOf[HttpBinClient])
trait HttpBinApi {
  def delay(seconds: Int): Upstream[JsValue]
  def headers(): Upstream[JsValue]
}
