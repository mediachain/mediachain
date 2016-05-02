package io.mediachain.util.cbor

import java.io.ByteArrayOutputStream

import io.mediachain.BaseSpec
import io.mediachain.util.cbor.CborAST._
import org.specs2.ScalaCheck

object CborASTSpec extends BaseSpec with ScalaCheck {
  import io.mediachain.util.cbor.CValueGenerators._
  import co.nstant.in.cbor.CborEncoder

  def is =
    s2"""
         - round-trip encodes to/from cbor-java DataItems $roundTripCborJava
      """

  def roundTripCborJava = prop { cVal: CValue =>
    val asDataItem = toDataItem(cVal)
    val converted = toDataItem(fromDataItem(asDataItem))


    // We're comparing the DataItem representation because it will not
    // fail if the ordering of map keys differs, whereas CMaps will not
    // be equal if the ordering is different
    asDataItem must_== converted

    // make sure byte representation is equal
    val out = new ByteArrayOutputStream
    new CborEncoder(out).encode(asDataItem)
    out.close()
    out.toByteArray must_== CborCodec.encode(cVal)
  }
}
