package org.mediachain.io

sealed abstract class ParsingError
object ParsingError {
  case class InvalidJson(message: String) extends ParsingError
}
