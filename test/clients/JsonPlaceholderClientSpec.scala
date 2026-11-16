package clients

import models.Todo
import models.UpstreamError._
import org.apache.pekko.pattern.{after => delayed} // ScalaTest has its own `after` word
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.routing.sird._
import play.api.test.Helpers.{await, defaultAwaitTimeout} // NOT Helpers._: its GET string clashes with sird's GET
import play.api.test.WsTestClient
import play.core.server.Server

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// Integration tests for the REAL client: real WSClient, real HTTP, real sockets,
// but the "upstream" is a tiny Play server we start on a random local port.
class JsonPlaceholderClientSpec extends PlaySpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  // Lesson 6 pays off: the base URL is config, so pointing the client at localhost is one line
  private def config(baseUrl: String) = Configuration(
    "upstreams.jsonplaceholder.base-url" -> baseUrl,
    "upstreams.jsonplaceholder.timeout"  -> "500 millis"
  )

  // Runs `block` with a client whose upstream is a fake server with scripted answers
  private def withFakeUpstream[T](block: JsonPlaceholderClient => T): T =
    Server.withRouterFromComponents() { components =>
      import components.{defaultActionBuilder => Action}
      {
        case GET(p"/todos/1")   => Action(Ok(Json.obj("userId" -> 1, "id" -> 1, "title" -> "write tests", "completed" -> false)))
        case GET(p"/todos/2")   => Action(Ok(Json.obj("id" -> 2, "oops" -> "no title here")))
        case GET(p"/todos/500") => Action(InternalServerError("database on fire"))
        case GET(p"/todos/429") => Action(TooManyRequests("slow down"))
        case GET(p"/todos/3")   => Action.async {
          // answers after 2s, the client gives up after 500ms. No thread sleeps.
          delayed(2.seconds)(Future.successful(Ok(Json.obj())))(components.actorSystem)
        }
        // anything else falls through to Play's default 404
      }
    } { implicit port =>
      WsTestClient.withClient { ws =>
        block(new JsonPlaceholderClient(ws, config(s"http://localhost:$port")))
      }
    }

  "JsonPlaceholderClient.todo" should {

    "parse a 200 into a Todo" in withFakeUpstream { client =>
      await(client.todo(1)) mustBe Right(Todo(userId = 1, id = 1, title = "write tests", completed = false))
    }

    "turn a 404 into Missing" in withFakeUpstream { client =>
      await(client.todo(404)) must matchPattern { case Left(Missing(url)) if url.endsWith("/todos/404") => }
    }

    "turn a 500 into BadStatus with the body" in withFakeUpstream { client =>
      await(client.todo(500)) mustBe Left(BadStatus(500, "database on fire"))
    }

    "turn a 429 into BadStatus too (Retry-After handling comes in lesson 9)" in withFakeUpstream { client =>
      await(client.todo(429)) must matchPattern { case Left(BadStatus(429, _)) => }
    }

    "turn a 200 with the wrong JSON into BadShape" in withFakeUpstream { client =>
      await(client.todo(2)) must matchPattern { case Left(BadShape(_)) => }
    }

    "turn a slow answer into TimedOut, after the configured 500ms" in withFakeUpstream { client =>
      val start  = System.nanoTime()
      val result = await(client.todo(3))
      val tookMs = (System.nanoTime() - start) / 1000000

      result must matchPattern { case Left(TimedOut(_)) => }
      tookMs must be < 1500L
    }
  }

  "a closed port" should {
    "give Unreachable, not an exception" in {
      WsTestClient.withClient { ws =>
        // port 1: nothing listens there, the OS refuses the connection at once
        val client = new JsonPlaceholderClient(ws, config("http://localhost:1"))
        await(client.todo(1)) must matchPattern { case Left(Unreachable(_, _)) => }
      }
    }
  }
}
