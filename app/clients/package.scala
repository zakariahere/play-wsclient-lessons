import models.UpstreamError

import scala.concurrent.Future

package object clients {
  type Upstream[A] = Future[Either[UpstreamError, A]]
}
