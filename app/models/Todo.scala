package models

import play.api.libs.json._


case class Todo(userId: Int, id: Int, title: String, completed: Boolean)

object Todo {
  implicit val format: OFormat[Todo] = Json.format[Todo]
}

