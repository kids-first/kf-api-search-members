package services

import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.testkit.DockerTests
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import play.api.test._

import scala.util.Try

class ESQueryServiceSpec extends FreeSpec with Matchers with DockerTests with BeforeAndAfterAll with MockitoSugar  {

  val m = mock[Turtle]

  override protected def beforeAll(): Unit = {
    Try {
      client.execute {
        deleteIndex("collapse")
      }.await
    }

    client.execute {
      createIndex("collapse") mappings {
        mapping() fields(
          keywordField("name"),
          keywordField("board")
        )
      }
    }.await

    client.execute {
      bulk(
        indexInto("collapse") id "1" fields("name" -> "Ibiza Playa", "board" -> "AI"),
        indexInto("collapse") id "2" fields("name" -> "Ibiza Playa", "board" -> "BB"),

        indexInto("collapse") id "3" fields("name" -> "Best Tenerife", "board" -> "AI")
      ).refresh(RefreshPolicy.Immediate)
    }.await
  }


}
