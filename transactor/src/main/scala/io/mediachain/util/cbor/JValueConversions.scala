package io.mediachain.util.cbor



object JValueConversions {
  import org.json4s._


  def jValueToCbor(jValue: JValue): CValue = {
    jValue match {
      case JNull => CNull()
      case JNothing => CUndefined()
      case JInt(num) => CInt(num)
      case JLong(num) => CLong(num)
      case JDecimal(num) => CDouble(num.doubleValue)
      case JDouble(num) => CDouble(num)
      case JBool(b) => CBool(b)
      case JString(s) => CString(s)
      case JArray(arr) => CArray(arr.map(jValueToCbor))
      case JObject(fields) => CMap(fields.map(f => (f._1, jValueToCbor(f._2))))
    }
  }
}
