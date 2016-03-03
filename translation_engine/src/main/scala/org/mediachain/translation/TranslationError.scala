package org.mediachain.translation

// TODO: extend this with error messages, etc.

trait TranslationError
case class InvalidFormatError() extends  TranslationError
