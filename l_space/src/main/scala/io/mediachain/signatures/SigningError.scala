package io.mediachain.signatures

import cats.data.Xor
import com.jsuereth.pgp.IncorrectPassphraseException

sealed trait SigningError
object SigningError {
  case class InvalidPassphrase(msg: String) extends SigningError

  def catchIncorrectPassphrase[T](f: => T): Xor[InvalidPassphrase, T] = {
    val x = Xor.catchOnly[IncorrectPassphraseException](f)
    x.leftMap(e => InvalidPassphrase(e.getMessage))
  }
}
