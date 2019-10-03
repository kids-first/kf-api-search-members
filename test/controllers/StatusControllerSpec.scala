package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import play.api.test._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class StatusControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "StatusController GET" should  {

    "render the status page from a new instance of controller" in {
      val controller = new StatusController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))
      status(home) mustBe OK
      contentAsString(home) must include ("Ok!")
    }

  }
}
