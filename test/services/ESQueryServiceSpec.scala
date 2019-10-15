package services

import com.sksamuel.elastic4s.http.search.SearchHit
import models.QueryFilter
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.StubControllerComponentsFactory
import utils.{MemberDocument, WithMemberIndex}

class ESQueryServiceSpec extends FlatSpec with WithMemberIndex with Matchers with BeforeAndAfterAll with StubControllerComponentsFactory with MockitoSugar {

  var configuration: Configuration = _
  var esQueryService: ESQueryService = _

  override def beforeAll(): Unit = {

    val members = Seq(
      MemberDocument("a1", "John", "DoeC", Some("jdoeemail@gmail.com"), roles = List("Community", "Research"), _title = Some("Dr.")),
      MemberDocument("a2", "John", "DoeA", Some("jdoeemail@gmail.com"), roles = List("Research"), _title = Some("M.")),
      MemberDocument("b1", "JohnC", "DoeB", Some("jdoeemail@gmail.com"), roles = List("Community"), _title = Some("Dr.")),
      MemberDocument("b2", "Doe", "John", Some("djohnemail@gmail.com"), interests = List("cancer", "pandas")),
      MemberDocument("c1", "Doe", "John", Some("djohnemail@yahoo.com")),
      MemberDocument("private_member", "Doe", "John", Some("djohnemail@gmail.com"), isPublic = false),
      MemberDocument("not_accepted_terms", "Doe", "John", Some("djohnemail@yahoo.com"), acceptedTerms = false)
    )
    populateIndex(members)

    configuration = Configuration.apply("elasticsearch.host" -> "localhost", "elasticsearch.ports" -> List(9200))

    esQueryService = new ESQueryService(configuration)
  }


  "ESQueryService" should "order documents by score" in {
    val result = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.map(r => r.score).toSeq
    result shouldBe result.sorted(Ordering.Float.reverse)
  }

  it should "order documents by lastName for same score" in {
    val hitResultsByScore: Seq[Seq[SearchHit]] = esQueryService.generateFilterQueries(QueryFilter("John", 0, 10)).await.right.get.result.hits.hits.toSeq.groupBy(_.score).values.toSeq
    val searchHitLastNames: Seq[Seq[String]] = hitResultsByScore.map(_.map(s => s.sourceAsMap.getOrElse("lastName", "").toString))

    searchHitLastNames.foreach(l => l shouldBe l.sorted(Ordering.String))
  }

  it should "enable Email to be searchable" in {
    //    esQueryService.generateFilterQueries(QueryFilter("gmail", 0, 10)).await.right.get.result.hits.hits.toSeq.foreach(p => println((Json.parse(p.sourceAsString).as[JsObject] ++ Json.obj("_id" -> p.id)).as[MemberDocument]))
    val results = esQueryService.generateFilterQueries(QueryFilter("gmail", 0, 10)).await.right.get.result.hits.hits.toSeq
    results.size shouldBe 4
  }

  it should "return only public members" in {
    val result: Seq[SearchHit] = esQueryService.generateFilterQueries(QueryFilter("John", 0, 100)).await.right.get.result.hits.hits.toSeq

    result.foreach {
      r => r.id shouldNot be("private_member")
    }
  }

  it should "return only members with acceptedTerms" in {
    val result: Seq[SearchHit] = esQueryService.generateFilterQueries(QueryFilter("John", 0, 100)).await.right.get.result.hits.hits.toSeq

    result.foreach {
      r => r.id shouldNot be("not_accepted_terms")
    }
  }

  it should "return desired fields" in {
    val result: Seq[SearchHit] = esQueryService.generateFilterQueries(QueryFilter("John", 0, 100)).await.right.get.result.hits.hits.toSeq

    result.foreach {
      r =>
        r.sourceAsMap.keys shouldNot contain("undesiredField")
        r.sourceAsMap.keys should contain theSameElementsAs Seq("firstName",
          "lastName",
          "email",
          "roles",
          "title",
          "institution",
          "city",
          "state",
          "country",
          "interests")
    }
  }

  it should "return all results with empty  highlight if queryString is empty" in {
    val result: Seq[SearchHit] = esQueryService.generateFilterQueries(QueryFilter("", 0, 100)).await.right.get.result.hits.hits.toSeq
    result.size shouldBe 5
    result.foreach {
      r =>
        r.highlight shouldBe null
    }
  }

  "generateCountQueries" should "return the total numbers of members, the total public and the total private fr a specific filter" in {
    val result: Map[String, Map[String, Int]] = esQueryService.generateCountQueries(QueryFilter("gmail", 0, 100)).await.right.get.result.aggregationsAsMap.asInstanceOf[Map[String, Map[String, Int]]]
    result should contain theSameElementsAs Map(
      "private" -> Map("doc_count" -> 1),
      "public" -> Map("doc_count" -> 4)
    )
  }
}