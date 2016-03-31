package io.mediachain.util

import scala.util.Try

object Env {
  def getString(key: String): Try[String] =
    Try(sys.env.getOrElse(key,
      throw new RuntimeException(s"$key environment var must be defined")))
}
