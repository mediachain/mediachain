package org.mediachain.signatures

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import cats.data.Xor
import com.jsuereth.pgp._
import org.json4s.JValue



class ObjectSigner(signingKey: SecretKey) {
  import org.mediachain.io.{CborMethods => Cbor}
  import org.mediachain.signatures.SigningError._


  def signText(text: String, passphrase: Array[Char]): Xor[SigningError, String] = {
    catchIncorrectPassphrase {
      signingKey.signString(text, passphrase)
    }
  }


  def signCborRepresentationOfJsonValue(json: JValue, passphrase: Array[Char])
  : Xor[SigningError, String] = {
    val canonicalCbor = Cbor.bytes(Cbor.render(json))
    catchIncorrectPassphrase {
      val input = new ByteArrayInputStream(canonicalCbor)
      val output = new ByteArrayOutputStream

      signingKey.signStream(input, output, passphrase)
      output.toString("UTF-8")
    }
  }

}
