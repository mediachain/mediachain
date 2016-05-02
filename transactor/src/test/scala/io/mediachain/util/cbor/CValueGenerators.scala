package io.mediachain.util.cbor

import org.scalacheck._
import Arbitrary.arbitrary
import io.mediachain.util.cbor.CborAST._
import co.nstant.in.cbor.model.{DataItem, RationalNumber, UnsignedInteger, LanguageTaggedString}

object CValueGenerators {


  val genCNull = Gen.const(CNull())
  val genCUndefined = Gen.const(CUndefined())
  val genCInt = for (i <- arbitrary[BigInt]) yield CInt(i)
  val genCDouble = for (d <- arbitrary[Double]) yield CDouble(d)
  val genCBool = for (b <- arbitrary[Boolean]) yield CBool(b)
  val genCString = for(s <- arbitrary[String]) yield CString(s)
  val genCBytes = for (b <- arbitrary[Array[Byte]]) yield CBytes(b)


  val genCPrimitive = Gen.oneOf(
    genCNull, genCUndefined, genCInt, genCDouble, genCBool, genCBytes, genCString
  )

  val genCArray = for {
    list <- Gen.containerOf[List, CValue](genCPrimitive)
  } yield CArray(list)


  val genCMap = for {
    keys <- Gen.containerOf[List, CValue](Gen.oneOf(genCInt, genCDouble, genCString))
    vals <- Gen.containerOfN[List, CValue](keys.length, genCPrimitive)
    kvs = (keys, vals).zipped.toList
  } yield CMap(kvs)


  // Generate some DataItems we don't unwrap for CUnhandled generator
  val genRational = for {
    numerator <- Gen.posNum[Long]
    denominator <- Gen.posNum[Long]
  } yield new RationalNumber(
    new UnsignedInteger(numerator),
    new UnsignedInteger(denominator)
  )

  val genTaggedString = for {
    lang <- Gen.oneOf("en", "fr", "es")
    str <- Gen.alphaStr
  } yield new LanguageTaggedString(lang, str)

  val genCUnhandled = for {
    dataItem <- Gen.oneOf[DataItem](genRational, genTaggedString)
  } yield CUnhandled(dataItem)

  val genCValue = Gen.oneOf(
    genCPrimitive, genCArray, genCMap,
    genCUnhandled
  )

  implicit def arbitraryCValue: Arbitrary[CValue] = Arbitrary(genCValue)
}
