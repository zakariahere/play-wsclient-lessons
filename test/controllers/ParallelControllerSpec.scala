package controllers

import clients.{HttpBinApi, TodoApi, Upstream}
import models.UpstreamError._
import models.{Post, Todo, UserSummary}
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

// Unit tests: no Play app, no network, no Guice. Just the controller and fakes.
class ParallelControllerSpec extends PlaySpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  // A hand-written fake: in-memory data + a log of which calls were made
  class FakeTodos(users: Map[Int, UserSummary], todos: Map[Int, Todo]) extends TodoApi {
    val calls = new ConcurrentLinkedQueue[String]()

    private def answer[A](call: String, found: Option[A]): Upstream[A] = {
      calls.add(call)
      Future.successful(found.toRight(Missing(s"fake:$call")))
    }

    def todo(id: Int): Upstream[Todo]              = answer(s"todo/$id", todos.get(id))
    def user(id: Int): Upstream[UserSummary]       = answer(s"user/$id", users.get(id))
    def todosFor(userId: Int): Upstream[Seq[Todo]] = answer(s"todosFor/$userId", Some(todos.values.filter(_.userId == userId).toSeq))
    def postsFor(userId: Int): Upstream[Seq[Post]] = answer(s"postsFor/$userId", Some(Seq(Post(1, userId, "t", "b"))))
  }

  object NoBin extends HttpBinApi {
    def delay(seconds: Int): Upstream[JsValue] = Future.successful(Right(Json.obj()))
    def headers(): Upstream[JsValue]           = Future.successful(Right(Json.obj()))
  }

  private val alice = UserSummary("Alice", "alice@example.com", "Luxembourg", "ACME", None)
  private val todos = Map(
    1 -> Todo(userId = 1, id = 1, title = "write tests", completed = false),
    2 -> Todo(userId = 1, id = 2, title = "ship it", completed = true),
    3 -> Todo(userId = 1, id = 3, title = "blog it", completed = false)
  )

  private def controllerWith(fake: TodoApi) = new ParallelController(fake, NoBin, stubControllerComponents())

  "profile" should {
    "combine user, open todos and posts" in {
      val result = controllerWith(new FakeTodos(Map(1 -> alice), todos)).profile(1)(FakeRequest())

      status(result) mustBe OK
      (contentAsJson(result) \ "user" \ "name").as[String] mustBe "Alice"
      (contentAsJson(result) \ "openTodos").as[Int] mustBe 2
      (contentAsJson(result) \ "posts").as[Int] mustBe 1
    }

    "return 404 when the user is missing, and still make all three calls" in {
      val fake   = new FakeTodos(Map.empty, todos)
      val result = controllerWith(fake).profile(99)(FakeRequest())

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] mustBe "not_found"
      // Lesson 5's trade-off, now pinned down by a test: futures created up front all run
      fake.calls.asScala.toSet mustBe Set("user/99", "todosFor/99", "postsFor/99")
    }
  }

  "many (best effort)" should {
    "return what was found and report what was not" in {
      val result = controllerWith(new FakeTodos(Map.empty, todos)).many("1,9999,2,abc")(FakeRequest())

      status(result) mustBe OK
      (contentAsJson(result) \ "found").as[Seq[Todo]].map(_.id) mustBe Seq(1, 2)
      (contentAsJson(result) \ "failed" \ 0 \ "id").as[Int] mustBe 9999
      (contentAsJson(result) \ "failed" \ 0 \ "error").as[String] mustBe "Missing"
    }
  }

  "allOrNothing" should {
    "return every todo when all exist" in {
      val result = controllerWith(new FakeTodos(Map.empty, todos)).allOrNothing("1,2,3")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result).as[Seq[Todo]].map(_.id) mustBe Seq(1, 2, 3)
    }

    "reject the whole batch on the first missing id" in {
      val result = controllerWith(new FakeTodos(Map.empty, todos)).allOrNothing("1,9999,2")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }
  }

  "toResult" should {
    "map every UpstreamError to its HTTP status" in {
      import UpstreamErrorResults.toResult
      toResult(Missing("u")).header.status mustBe NOT_FOUND
      toResult(BadStatus(500, "boom")).header.status mustBe BAD_GATEWAY
      toResult(BadShape(Json.obj())).header.status mustBe BAD_GATEWAY
      toResult(TimedOut("u")).header.status mustBe GATEWAY_TIMEOUT
      toResult(Unreachable("u", "dns")).header.status mustBe BAD_GATEWAY
    }
  }
}
