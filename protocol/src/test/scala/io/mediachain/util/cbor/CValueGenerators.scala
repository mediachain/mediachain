package io.mediachain.util.cbor

import org.scalacheck._
import Arbitrary.arbitrary
import io.mediachain.util.cbor.CborAST._
import co.nstant.in.cbor.model.{DataItem, RationalNumber, UnsignedInteger, LanguageTaggedString}

object CValueGenerators {
  import io.mediachain.util.gen.GenInstances._
  import cats.syntax.cartesian._

  val genCNull = Gen.const(CNull)
  val genCUndefined = Gen.const(CUndefined)
  val genCTag = arbitrary[Long].map(CTag)
  val genCInt = arbitrary[BigInt].map(CInt)
  val genCDouble = arbitrary[Double].map(CDouble)
  val genCBool = arbitrary[Boolean].map(CBool)
  val genCString = arbitrary[String].map(CString)
  val genCBytes = arbitrary[Array[Byte]].map(CBytes)


  val genCPrimitive = Gen.oneOf(
    genCNull, genCUndefined, genCTag, genCInt, genCDouble, genCBool,genCBytes,
    genCString
  )

  val genCArray = for {
    list <- Gen.containerOf[List, CValue](genCPrimitive)
  } yield CArray(list)

  val genCMap = for {
    keys <- Gen.containerOf[List, CValue](
      Gen.oneOf(genCInt, genCDouble, genCString)
    )
    vals <- Gen.containerOfN[List, CValue](
      keys.length, genCPrimitive
    )
    kvs = (keys, vals).zipped.toList
  } yield CMap(kvs)

  // Generate some DataItems we don't unwrap for CUnhandled generator
  val genRational =
    (Gen.posNum[Long].map(new UnsignedInteger(_)) |@|
      Gen.posNum[Long].map(new UnsignedInteger(_))
      ).map(new RationalNumber(_, _))

  val genTaggedString =
    (Gen.oneOf("en", "fr", "es") |@| Gen.alphaStr)
      .map(new LanguageTaggedString(_, _))

  val genCUnhandled = Gen.oneOf[DataItem](genRational, genTaggedString)
    .map(CUnhandled)

  val genCValue =Gen.oneOf(genCPrimitive, genCArray, genCMap, genCUnhandled)

  implicit def arbitraryCValue: Arbitrary[CValue] = Arbitrary(genCValue)
}
