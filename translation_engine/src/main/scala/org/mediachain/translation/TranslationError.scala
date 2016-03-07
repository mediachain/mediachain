package org.mediachain.translation

// TODO: extend this with error messages, etc.

sealed abstract class TranslationError

object TranslationError {
  case class InvalidFormatError() extends TranslationError
}
