package models

import play.api.libs.json.{Json, OFormat}

final case class NewPost(userId: Int, title: String, body: String)
object NewPost {
  implicit val format: OFormat[NewPost] = Json.format[NewPost]
}

final case class Post(id: Int, userId: Int, title: String, body: String)
object Post {
  implicit val format: OFormat[Post] = Json.format[Post]
}
