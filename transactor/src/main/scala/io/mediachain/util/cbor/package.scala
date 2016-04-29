package io.mediachain.util

import java.io.ByteArrayOutputStream

import co.nstant.in.cbor.CborEncoder


package object cbor {
  import co.nstant.in.cbor.builder.AbstractBuilder
  import co.nstant.in.cbor.model.{DataItem, SimpleValue, Array => DataItemArray, Map => DataItemMap}


  sealed trait CValue
  case class CNull() extends CValue
  case class CUndefined() extends CValue
  case class CInt(num: BigInt) extends CValue
  case class CLong(num: Long) extends CValue
  case class CDouble(num: Double) extends CValue
  case class CBool(bool: Boolean) extends CValue
  case class CString(string: String) extends CValue
  case class CBytes(bytes: Array[Byte]) extends CValue
  case class CArray(items: List[CValue]) extends CValue
  case class CMap(fields: List[CField]) extends CValue
  type CField = (String, CValue)



  def toDataItem(cValue: CValue): DataItem = cValue match {
    case _: CNull => SimpleValue.NULL
    case _: CUndefined => SimpleValue.UNDEFINED
    case CInt(num) => converter.convert(num)
    case CLong(num) => converter.convert(num)
    case CDouble(num) => converter.convert(num)
    case CBool(bool) => converter.convert(bool)
    case CString(string) => converter.convert(string)
    case CBytes(bytes) => converter.convert(bytes)
    case CArray(items) => {
      val arrayDataItem = new DataItemArray(items.length)
      items.foreach(item => arrayDataItem.add(toDataItem(item)))
      arrayDataItem
    }

    case CMap(fields) => {
      val mapDataItem = new DataItemMap(fields.length)
      fields.foreach(field => mapDataItem.put(
        toDataItem(CString(field._1)), toDataItem(field._2)
      ))
      mapDataItem
    }
  }

  def encode(cValue: CValue): Array[Byte] = {
    val out = new ByteArrayOutputStream
    new CborEncoder(out).encode(toDataItem(cValue))
    out.close()
    out.toByteArray
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
