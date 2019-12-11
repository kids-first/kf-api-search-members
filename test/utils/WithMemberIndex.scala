package utils

import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.testkit.DockerTests
import com.sksamuel.elastic4s.{ElasticsearchClientUri, Index, Indexable, RefreshPolicy}
import play.api.libs.json.Json


trait WithMemberIndex extends DockerTests {

  def populateIndex(documents: Seq[MemberDocument]): Unit = {
    val elasticsearchClientUri = ElasticsearchClientUri("localhost", 9200)
    val esClient = HttpClient(elasticsearchClientUri)


    esClient.execute {
      deleteIndex(IndexName)
    }.await

    esClient.execute {
      createIndex(IndexName).mappings(
        mapping(IndexName)
          .fields(
            textField("firstName").fields(keywordField("raw")),
            textField("lastName").fields(keywordField("raw")),
            keywordField("email"),
            keywordField("hashedEmail"),
            textField("institutionalEmail"),
            keywordField("acceptedTerms"),
            booleanField("isPublic"),
            keywordField("roles"),
            keywordField("title"),
            textField("jobTitle").fields(keywordField("raw")),
            textField("institution").fields(keywordField("raw")),
            textField("city").fields(keywordField("raw")),
            textField("state").fields(keywordField("raw")),
            textField("country").fields(keywordField("raw")),
            keywordField("eraCommonsID"),
            textField("bio"),
            textField("story"),
            textField("interests").fields(keywordField("raw")),
            nestedField("virtualStudies").fields(keywordField("id"), textField("name").fields(keywordField("raw"))),
            nestedField("searchableInterests").fields(textField("name").fields(keywordField("raw")))
          )
      )
    }.await


    esClient.execute(
      bulk(
        documents.map(member => indexRequest(member._id, member))
      ).refresh(RefreshPolicy.Immediate)
    ).await


  }


  //  Can't use Writes of MemberDocument directly (need to remove parameter "_id", need to create a new one...
  //  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>  Json.toJson(t).toString()
  implicit val MemberIndexable: Indexable[MemberDocument] = (t: MemberDocument) =>
    Json.obj(
      "firstName" -> t.firstName,
      "lastName" -> t.lastName,
      "email" -> t.email,
      "hashedEmail" -> t.email.map(e => md5HashString(e)),
      "isPublic" -> t.isPublic,
      "acceptedTerms" -> t.acceptedTerms,
      "roles" -> t.roles,
      "title" -> t._title,
      "institution" -> t.institution,
      "city" -> t.city,
      "state" -> t.state,
      "country" -> t.country,
      "interests" -> t.interests,
      "bio" -> t.bio,
      "story" -> t.story,
      "searchableInterests" -> t.interests.map(i => Json.obj("name"-> i)),
      "undesiredField" -> "undesired"
    ).toString()



  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  private val IndexName = "member"

  def md5HashString(s: String): String = {
    import java.security.MessageDigest
    import java.math.BigInteger
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(s.getBytes)
    val bigInt = new BigInteger(1,digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}