package controllers

import cats.data.EitherT
import cats.syntax.all._
import clients.{HttpBinClient, JsonPlaceholderClient}
import controllers.UpstreamErrorResults.toResult
import jakarta.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ParallelController @Inject()(jp: JsonPlaceholderClient,
                                   bin: HttpBinClient,
                                   val controllerComponents: ControllerComponents)
                                  (implicit ec: ExecutionContext) extends BaseController {

  // By-name parameter: the clock starts BEFORE the futures are created
  private def timed[A](work: => Future[A]): Future[(A, Long)] = {
    val start = System.nanoTime()
    work.map(a => (a, (System.nanoTime() - start) / 1000000))
  }

  // 1) Sequential: each call is created inside the for, after the previous one finished
  def sequential(): Action[AnyContent] = Action.async {
    timed {
      for {
        a <- bin.delay(1)
        b <- bin.delay(1)
        c <- bin.delay(1)
      } yield List(a, b, c)
    }.map { case (results, ms) =>
      Ok(Json.obj("mode" -> "sequential", "tookMs" -> ms, "succeeded" -> results.count(_.isRight)))
    }
  }

  // 2) Parallel: create all three futures FIRST, then combine them
  def parallel(): Action[AnyContent] = Action.async {
    timed {
      val fa = bin.delay(1)
      val fb = bin.delay(1)
      val fc = bin.delay(1)
      for {
        a <- fa
        b <- fb
        c <- fc
      } yield List(a, b, c)
    }.map { case (results, ms) =>
      Ok(Json.obj("mode" -> "parallel", "tookMs" -> ms, "succeeded" -> results.count(_.isRight)))
    }
  }

  // 3) Three different calls in parallel, combined with EitherT
  def profile(id: Int): Action[AnyContent] = Action.async {
    val fUser  = jp.user(id)
    val fTodos = jp.todosFor(id)
    val fPosts = jp.postsFor(id)

    val result = for {
      user  <- EitherT(fUser)
      todos <- EitherT(fTodos)
      posts <- EitherT(fPosts)
    } yield Json.obj("user" -> user, "openTodos" -> todos.count(!_.completed), "posts" -> posts.size)

    result.value.map(_.fold(toResult, Ok(_)))
  }

  private def parseIds(ids: String): List[Int] = ids.split(",").toList.flatMap(_.trim.toIntOption)

  // 4) N calls in parallel: keep the good ones AND report the bad ones
  def many(ids: String): Action[AnyContent] = Action.async {
    Future.traverse(parseIds(ids))(id => jp.todo(id).map(id -> _)).map { results =>
      val found  = results.collect { case (_, Right(todo)) => todo }
      val failed = results.collect { case (id, Left(err)) => Json.obj("id" -> id, "error" -> err.productPrefix) }
      Ok(Json.obj("found" -> found, "failed" -> failed))
    }
  }

  // 5) N calls in parallel, all-or-nothing: the first Left wins
  def allOrNothing(ids: String): Action[AnyContent] = Action.async {
    Future.traverse(parseIds(ids))(jp.todo).map { results =>
      results.sequence.fold(toResult, todos => Ok(Json.toJson(todos)))
    }
  }

  // 6) Bounded parallelism: at most `size` calls in flight at a time
  private def inBatches[A, B](items: List[A], size: Int)(f: A => Future[B]): Future[List[B]] =
    items.grouped(size).foldLeft(Future.successful(List.empty[B])) { (accF, batch) =>
      accF.flatMap(acc => Future.traverse(batch)(f).map(acc ++ _))
    }

  def batched(n: Int, size: Int): Action[AnyContent] = Action.async {
    timed(inBatches((1 to n).toList, size)(jp.todo)).map { case (results, ms) =>
      Ok(Json.obj("calls" -> n, "batchSize" -> size, "batches" -> (n + size - 1) / size,
                  "tookMs" -> ms, "succeeded" -> results.count(_.isRight)))
    }
  }
}
