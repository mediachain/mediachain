package io.mediachain.util

import org.specs2.Specification

object CborSpec extends Specification {
  import org.json4s.jackson.{JsonMethods => Json}
  import io.mediachain.util.{CborMethods => Cbor}

  def is =
    s2"""
      $sortsKeysBeforeSerializing - serializes objects that differ by key ordering to the same bytes
      """


  def sortsKeysBeforeSerializing = {
    val a = """ {"foo": "bar", "baz": "qux"} """
    val b = """ {"baz": "qux", "foo": "bar"} """

    val cborA = Cbor.bytes(Cbor.render(Json.parse(a)))
    val cborB = Cbor.bytes(Cbor.render(Json.parse(b)))

    cborA must_== cborB
  }
}
