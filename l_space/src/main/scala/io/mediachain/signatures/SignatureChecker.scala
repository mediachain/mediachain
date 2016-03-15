package io.mediachain.signatures

import java.io.ByteArrayInputStream

import cats.data.Xor
import com.jsuereth.pgp.{PGP, PublicKeyLike}
import io.mediachain.util.{CborSerializer, ParsingError}
import org.json4s.JValue

class SignatureChecker(pubkey: PublicKeyLike) {
  PGP.init

  def verifySignedString(string: String, signature: String): Boolean = {
    pubkey.verifySignatureString(string, signature)
  }

  def verifySignedBytes(bytes: Array[Byte], signature: String): Boolean = {
    val byteStream = new ByteArrayInputStream(bytes)
    val sigStream = new ByteArrayInputStream(signature.getBytes("UTF-8"))
    pubkey.verifySignatureStreams(byteStream, sigStream)
  }

  def verifySignedJsonText(jsonText: String, signature: String): Xor[ParsingError, Boolean] = {
    val canonicalBytes = CborSerializer.bytesForJsonText(jsonText)
    canonicalBytes.map(verifySignedBytes(_, signature))
  }

  def verifySignedJsonObject(json: JValue, signature: String): Boolean = {
    val canonicalBytes = CborSerializer.bytesForJsonValue(json)
    verifySignedBytes(canonicalBytes, signature)
  }
}
