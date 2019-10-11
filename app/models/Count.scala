package models

import play.api.libs.json.{Json, Writes}

case class Count(total: Int, publicMembers: Int, privateMembers: Int)

object Count {
  implicit val countWrites: Writes[Count] = Json.writes[Count]
}
