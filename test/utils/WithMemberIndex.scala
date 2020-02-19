package utils

import com.sksamuel.elastic4s.analyzers.{CustomAnalyzerDefinition, EdgeNGramTokenFilter, KeywordTokenizer, LowercaseTokenFilter, StandardTokenizer}
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
val createQuery = createIndex(IndexName)
  .mappings(
    mapping(IndexName)
      .fields(
        textField("firstName").analyzer("autocomplete").fields(keywordField("raw")),
        textField("lastName").analyzer("autocomplete").fields(keywordField("raw")),
        keywordField("email"),
        keywordField("hashedEmail"),
        textField("institutionalEmail"),
        keywordField("acceptedTerms"),
        booleanField("isPublic"),
        booleanField("isActive"),
        keywordField("roles"),
        keywordField("title"),
        textField("jobTitle").analyzer("autocomplete").fields(keywordField("raw")),
        textField("institution").analyzer("autocomplete").fields(keywordField("raw")),
        textField("city").analyzer("autocomplete").fields(keywordField("raw")),
        textField("state").analyzer("autocomplete").fields(keywordField("raw")),
        textField("country").analyzer("autocomplete").fields(keywordField("raw")),
        keywordField("eraCommonsID"),
        textField("bio").analyzer("autocomplete"),
        textField("story").analyzer("autocomplete"),
        textField("interests").analyzer("autocomplete").fields(keywordField("raw")),
        nestedField("virtualStudies").fields(keywordField("id"), textField("name").analyzer("autocomplete").fields(keywordField("raw"))),
        nestedField("searchableInterests").fields(textField("name").analyzer("autocomplete").fields(keywordField("raw")))
      )
  ).analysis(CustomAnalyzerDefinition(
  "autocomplete",
  StandardTokenizer,
  LowercaseTokenFilter,
  EdgeNGramTokenFilter("edge_ngram", minGram = Some(1), maxGram = Some(20), side = Some("front"))
))
    logger.warn(esClient.show(createQuery))
   val createResult =  esClient.execute(createQuery).await

    createResult.left.map(f => throw new IllegalStateException(s"Error during index creation $f"))

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
      "isActive" -> t.isActive,
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
      "searchableInterests" -> t.interests.map(i => Json.obj("name" -> i)),
      "undesiredField" -> "undesired"
    ).toString()


  def indexRequest(id: String, member: MemberDocument): IndexDefinition = indexInto(Index(IndexName), IndexName).source(member).id(id)

  private val IndexName = "member"

  def md5HashString(s: String): String = {

    import java.math.BigInteger
    import java.security.MessageDigest

    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(s.getBytes)
    val bigInt = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}