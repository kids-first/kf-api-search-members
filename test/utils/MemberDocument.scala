package utils

case class MemberDocument(
                           _id: String,
                           firstName: String,
                           lastName: String,
                           email: Option[String],
                           acceptedTerms: Boolean = true,
                           isPublic: Boolean = true,
                           roles: List[String] = Nil,
                           _title: Option[String] = None,
                           institution: Option[String] = None,
                           city: Option[String] = None,
                           state: Option[String] = None,
                           country: Option[String] = None,
                           interests: List[String] = Nil,
                           bio:Option[String] = None,
                           story: Option[String] = None
                         )
