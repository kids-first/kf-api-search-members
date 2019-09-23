package models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class MemberDocument(
                   firstName: String,
                   lastName: String,
                   email: Option[String],
//                   institutionalEmail: Option[String],
//                   acceptedTerms: Boolean = false,
                   isPublic: Boolean,
//                   roles: List[String] = Nil,
//                   title: Option[String],
//                   jobTitle: Option[String],
                   institution: Option[String],
                   city: Option[String],
                   state: Option[String],
                   country: Option[String],
//                   eraCommonsID: Option[String],
//                   bio: Option[String],
//                   story: Option[String],
                   interests: List[String] = Nil
//                   virtualStudies: List[String] = Nil
                   )

object MemberDocument {

  implicit val memberDocumentReads: Reads[MemberDocument] = (
    (JsPath \\"firstName").read[String] and
      (JsPath \\ "lastName").read[String] and
      (JsPath \\ "email").readNullable[String] and
      (JsPath \\ "isPublic").readNullable[Boolean].map(_.getOrElse(false)) and
      (JsPath \\ "institution").readNullable[String] and
      (JsPath \\ "city").readNullable[String] and
      (JsPath \\ "state").readNullable[String] and
      (JsPath \\ "country").readNullable[String] and
      (JsPath \\ "interests").read[List[String]]
    )(MemberDocument.apply _)

  implicit val memberDocumentWrites: Writes[MemberDocument] = (
    (JsPath \ "firstName").write[String] and
      (JsPath \ "lastName").write[String] and
      (JsPath \ "email").writeNullable[String] and
      (JsPath \ "isPublic").write[Boolean] and
      (JsPath \ "institution").writeNullable[String] and
      (JsPath \ "city").writeNullable[String] and
      (JsPath \ "state").writeNullable[String] and
      (JsPath \ "country").writeNullable[String] and
      (JsPath \ "interests").write[List[String]]
    )(unlift(MemberDocument.unapply))

}
