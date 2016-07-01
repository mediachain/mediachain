package io.mediachain.util

import org.slf4j.Logger

object Logging {
  def withErrorLog(logger: Logger)(f: => Unit) = {
    try { f }
    catch {
      case e: Throwable => 
        logger.error("Unhandled exception", e)
    }
  }
}

