package io.mediachain.util.cbor

object CborCodec {
  import java.io.ByteArrayOutputStream
  import co.nstant.in.cbor.{CborDecoder, CborEncoder}
  import io.mediachain.util.cbor.CborAST._
  import collection.JavaConverters._

  def encode(cValue: CValue): Array[Byte] = {
    val out = new ByteArrayOutputStream
    new CborEncoder(out).encode(CborAST.toDataItem(cValue))
    out.close()
    out.toByteArray
  }

  def decode(bytes: Array[Byte]): List[CValue] =
    CborDecoder.decode(bytes).asScala.map(CborAST.fromDataItem).toList
}
