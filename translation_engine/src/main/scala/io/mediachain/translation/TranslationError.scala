package io.mediachain.translation

sealed abstract class TranslationError

object TranslationError {
  case class ParsingFailed(underlying: Throwable) extends TranslationError
  case class ResourceNotReadable(underlying: Throwable) extends TranslationError
  case class InvalidFormat() extends TranslationError
}
