package clients

import com.typesafe.scalalogging.LazyLogging
import play.api.libs.ws.{WSRequestExecutor, WSRequestFilter}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

// Runs around EVERY call made through a request that has this filter attached
class LoggingFilter(upstream: String)(implicit ec: ExecutionContext) extends WSRequestFilter with LazyLogging {

  override def apply(next: WSRequestExecutor): WSRequestExecutor = WSRequestExecutor { request =>
    val correlationId = UUID.randomUUID().toString.take(8)
    val start = System.nanoTime()

    def tookMs = (System.nanoTime() - start) / 1000000

    next(request.addHttpHeaders("X-Correlation-Id" -> correlationId)).andThen {
      case Success(r) =>
        logger.info(s"[$correlationId] $upstream ${request.method} ${request.url} -> ${r.status} in ${tookMs}ms")
      case Failure(e) =>
        logger.warn(s"[$correlationId] $upstream ${request.method} ${request.url} -> FAILED ${e.getClass.getSimpleName} in ${tookMs}ms")
    }
  }
}
