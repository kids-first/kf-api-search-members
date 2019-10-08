package models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.Results

class MemberDocumentSpec extends PlaySpec with Results {

  private val minimalJson = Json.parse("""
  {
    "_id": "123",
    "firstName" : "FN",
    "lastName" : "LN",
    "interests" : [],
    "roles": []
  }
  """)

  "JSON" should {
    "be successfully parsed when expected" in {
      minimalJson.as[MemberDocument] mustBe(MemberDocument(_id = "123" ,firstName = "FN", lastName = "LN", isPublic = false, email = None, roles = Nil, _title = None, institution = None, city = None, state = None, country = None, interests = Nil))
    }

//    "fail to be parsed if expected" in {
//      Json.parse("""{"unexpected":"json"}""") must beLike[JsResult[MemberDocument]] {
//        case JsError(details) =>
//          ok // possible check `details`
//      }
//    }
  }

}
