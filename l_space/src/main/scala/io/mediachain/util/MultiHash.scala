package io.mediachain.util

import java.util

import cats.data.Xor
import io.mediachain.Types.Hashable
import io.mediachain.util.MultiHash.HashType
import io.mediachain.util.MultiHashError._
import io.mediachain.util.ParsingError.ConversionToJsonFailed


sealed abstract class MultiHashError
object MultiHashError {
  case class UnknownMultiHashType(message: String) extends MultiHashError
  case class InvalidHashLength(message: String) extends MultiHashError
  case class DecodingFailed(message: String) extends MultiHashError
}


object MultiHash {
  import java.security.MessageDigest

  import scala.language.implicitConversions

  sealed abstract class HashType(val name: String, val index: Byte, val length: Byte)

  case object sha1 extends HashType("SHA-1", 0x11, 20)
  case object sha256 extends HashType("SHA-256", 0x12, 32)
  case object sha512 extends HashType("SHA-512", 0x13, 64)
  case object sha3 extends HashType("SHA-3", 0x14, 64)
  case object blake2b extends HashType("BLAKE2b", 0x40, 64)
  case object blake2s extends HashType("BLAKE2s", 0x41, 32)

  val lookup: Map[Byte, HashType] = Map[Byte, HashType](
    sha1.index -> sha1,
    sha256.index -> sha256,
    sha512.index -> sha512,
    sha3.index -> sha3,
    blake2b.index -> blake2b,
    blake2s.index -> blake2s
  )

  /**
    * Return a MultiHash of `contents` using SHA-256.
    * This is the IPFS default hash algorithm.
    *
    * @param contents an array of bytes to hash
    * @return a MultiHash describing the hashed contents
    */
  def hashWithSHA256(contents: Array[Byte]): MultiHash = {
    val digest = MessageDigest.getInstance("SHA-256")

    // Creating a MultiHash from a valid SHA-256 digest should never fail,
    // so it seems like a decent candidate for an actual Exception
    fromHash(sha256, digest.digest(contents))
      .getOrElse(throw new IllegalStateException("Creation of SHA-256 MultiHash failed."))
  }


  def forHashable[H <: Hashable](h: H): Xor[ConversionToJsonFailed, MultiHash] = {
    CborSerializer.bytesForHashable(h)
        .map(hashWithSHA256)
  }

  def fromHash(hashType: HashType, hash: Array[Byte]): Xor[MultiHashError, MultiHash] = {
    if (hash.length > 127) {
      Xor.left(InvalidHashLength("MultiHash length must not exceed 127 bytes, but you gave me " +
        hash.length))
    } else if (hash.length != hashType.length) {
      Xor.left(InvalidHashLength("Hash type " + hashType.name + " has length of " +
        hashType.length + ", but you gave me " + hash.length))
    } else {
      Xor.right(MultiHash(hashType, hash))
    }
  }


  def fromBytes(multihash: Array[Byte]): Xor[MultiHashError, MultiHash] = {
    val index = multihash(0)
    val hash = multihash.drop(2)
    val hashTypeXor: Xor[MultiHashError, HashType] =
      Xor.fromOption(lookup.get(index), UnknownMultiHashType("Unknown MultiHash type " + index))

    hashTypeXor.flatMap(fromHash(_, hash))
  }


  def fromHex(hex: String): Xor[MultiHashError, MultiHash] = {
    if (hex.length % 2 != 0) {
      Xor.left(DecodingFailed("A hex string must have an even number of digits, " +
        "but you gave me a string with length " + hex.length))
    } else {
      val bytes: Array[Byte] = hex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
      fromBytes(bytes)
    }
  }


  def fromBase58(base58: String): Xor[MultiHashError, MultiHash] =
    fromBytes(Base58.decode(base58))


  implicit def toByteArray(multiHash: MultiHash): Array[Byte] =
    multiHash.bytes
}


case class MultiHash private (hashType: HashType, hash: Array[Byte]) {

  def bytes: Array[Byte] = {
    val header = Array[Byte](hashType.index, hashType.length)
    header ++ hash
  }


  def hex: String = {
    bytes.map("%02x".format(_)).mkString
  }


  def base58: String = {
    Base58.encode(bytes)
  }


  override def equals(other: Any): Boolean = other match {
    case that: MultiHash => that.bytes.sameElements(this.bytes)
    case _ => false
  }


  override def hashCode = util.Arrays.hashCode(bytes)


  override def toString: String = s"MultiHash[${hashType.name}]: $base58"
}
