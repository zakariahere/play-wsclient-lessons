package models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Json, OWrites, Reads, __}

case class UserSummary(name: String,
                       email: String,
                       city: String,
                       company: String,
                       website: Option[String])

object UserSummary {
  implicit val reads: Reads[UserSummary] = (
    (__ \ "name").read[String] and
      (__ \ "email").read[String](Reads.email) and
      (__ \ "address" \ "city").read[String] and
      (__ \ "company" \ "name").read[String] and
      (__ \ "website").readNullable[String]
    )(UserSummary.apply _)

  implicit val writes: OWrites[UserSummary] = Json.writes[UserSummary]
}