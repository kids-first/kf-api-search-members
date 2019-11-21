package controllers

import org.scalatest.{FlatSpec, Matchers}

class SearchControllerSpec extends FlatSpec with Matchers{


  "fromCount" should "extract the number contain in the given bucket" in {
    val aggs = Map("private"-> Map("doc_count" -> 12),"public"-> Map("doc_count" -> 24))

    SearchController.fromCount("private", aggs) shouldBe 12

  }

  it should "extract 0 if the key not in the given bucket" in {
    val aggs = Map("private"-> Map("doc_count" -> 12),"public"-> Map("doc_count" -> 24))

    SearchController.fromCount("unknown", aggs) shouldBe 0

  }

  it should "extract 0 if doc_count not exist for the given bucket" in {
    val aggs = Map("private"-> Map("something_else" -> 12),"public"-> Map("doc_count" -> 24))

    SearchController.fromCount("private", aggs) shouldBe 0

  }
}
