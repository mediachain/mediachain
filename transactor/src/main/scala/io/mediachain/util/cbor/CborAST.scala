package io.mediachain.util.cbor



object CborAST {
  import co.nstant.in.cbor.builder.AbstractBuilder
  import co.nstant.in.cbor.model.SimpleValueType
  import co.nstant.in.cbor.{model => Cbor}

  import collection.JavaConverters._
  import scala.util.Try


  sealed trait CValue
  case class CNull() extends CValue
  case class CUndefined() extends CValue
  case class CInt(num: BigInt) extends CValue
  case class CDouble(num: Double) extends CValue
  case class CBool(bool: Boolean) extends CValue
  case class CString(string: String) extends CValue
  case class CBytes(bytes: Array[Byte]) extends CValue
  case class CArray(items: List[CValue]) extends CValue
  case class CMap(fields: List[CField]) extends CValue {
    def asStringKeyedMap: Map[String, CValue] = {
      val stringKeyedFields = fields.flatMap { f: CField =>
        f._1 match {
          case CString(s) => Some((s, f._2))
          case _ => None
        }
      }
      Map(stringKeyedFields:_*)
    }

    def getAs[T <: CValue](key: CValue): Option[T] =
      fields.toMap.get(key).flatMap(v => Try(v.asInstanceOf[T]).toOption)


    def getAs[T <: CValue](key: String): Option[T] =
      getAs(CString(key))
    
  }
  type CField = (CValue, CValue)

  // catch-all type for things we aren't interested in unpacking to a
  // specific CValue.
  // e.g. cbor's Rational type, Language-tagged strings, etc.
  case class CUnhandled(dataItem: Cbor.DataItem) extends CValue


  object CMap {
    def apply(fields: CField*): CMap = CMap(fields.toList)

    // would like to have used `apply` here, but thanks to type erasure
    // the JVM can't distinguish between a List[(CValue, CValue)] and a
    // List[(String, CValue)]
    def withStringKeys(stringFields: List[(String, CValue)]): CMap =
      CMap(stringFields.map(f => (CString(f._1), f._2)))

    def withStringKeys(stringFields: (String, CValue)*): CMap =
      withStringKeys(stringFields.toList)
  }

  def toDataItem(cValue: CValue): Cbor.DataItem = cValue match {
    case _: CNull => Cbor.SimpleValue.NULL
    case _: CUndefined => Cbor.SimpleValue.UNDEFINED
    case CInt(num) => converter.convert(num)
    case CDouble(num) => converter.convert(num)
    case CBool(bool) => converter.convert(bool)
    case CString(string) => converter.convert(string)
    case CBytes(bytes) => converter.convert(bytes)
    case CArray(items) => {
      val arrayDataItem = new Cbor.Array(items.length)
      items.foreach(item => arrayDataItem.add(toDataItem(item)))
      arrayDataItem
    }

    case CMap(fields) => {
      val mapDataItem = new Cbor.Map(fields.length)
      fields.foreach(field => mapDataItem.put(
        toDataItem(field._1), toDataItem(field._2)
      ))
      mapDataItem
    }

    case CUnhandled(item) => item
  }

  def fromDataItem(item: Cbor.DataItem): CValue = item match {
      // match these first, since they're encoded as Arrays and would
      // otherwise match CArray
    case _: Cbor.LanguageTaggedString => CUnhandled(item)
    case _: Cbor.RationalNumber => CUnhandled(item)

    case s: Cbor.SimpleValue if s.getSimpleValueType == SimpleValueType.TRUE =>
      CBool(true)
    case s: Cbor.SimpleValue if s.getSimpleValueType == SimpleValueType.FALSE =>
      CBool(false)
    case s: Cbor.SimpleValue if s.getSimpleValueType == SimpleValueType.NULL =>
      CNull()
    case s: Cbor.SimpleValue if s.getSimpleValueType == SimpleValueType.UNDEFINED =>
      CUndefined()

    case i: Cbor.UnsignedInteger => CInt(i.getValue)
    case i: Cbor.NegativeInteger => CInt(i.getValue)
    case f: Cbor.HalfPrecisionFloat => CDouble(f.getValue)
    case f: Cbor.SinglePrecisionFloat => CDouble(f.getValue)
    case f: Cbor.DoublePrecisionFloat => CDouble(f.getValue)
    case n: Cbor.Number => CInt(n.getValue)

    case b: Cbor.ByteString => CBytes(b.getBytes)
    case s: Cbor.UnicodeString => CString(s.getString)
    case a: Cbor.Array =>
      CArray(a.getDataItems.asScala.map(fromDataItem).toList)

    case m: Cbor.Map => {
      val keys = m.getKeys.asScala
      val fields = keys.map { k =>
        (fromDataItem(k), fromDataItem(m.get(k)))
      }
      CMap(fields.toList)
    }

    case _ => CUnhandled(item)
  }


  private val converter = new DataItemConverter
  private class DataItemConverter extends AbstractBuilder[Object](null) {
    def convert(num: BigInt) = super.convert(num.bigInteger)
    def convert(num: BigDecimal) = super.convert(num.doubleValue)
    override def convert(num: Long) = super.convert(num)
    override def convert(num: Double) = super.convert(num)
    override def convert(boolean: Boolean) = super.convert(boolean)
    override def convert(string: String) = super.convert(string)
    override def convert(bytes: Array[Byte]) = super.convert(bytes)
  }
}
