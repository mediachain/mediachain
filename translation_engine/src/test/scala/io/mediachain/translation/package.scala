package io.mediachain.translation

import com.fasterxml.jackson.core.JsonFactory

trait Implicit {
  implicit val factory = new JsonFactory
}
object `package` extends Implicit