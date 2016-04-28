package io.mediachain.util.cbor


object JValueConversions {
  import co.nstant.in.cbor.builder.AbstractBuilder
  import co.nstant.in.cbor.{model => Cbor}
  import org.json4s._

  // Provides access to protected `convert` methods on `AbstractBuilder`
  private class StatelessBuilder extends AbstractBuilder[Object](null) {
    def convertNumber(num: BigInt) = super.convert(num.bigInteger)
    def convertNumber(num: BigDecimal) = super.convert(num.doubleValue)
    def convertNumber(num: Long) = super.convert(num)
    def convertNumber(num: Double) = super.convert(num)
    def convertBool(boolean: Boolean) = super.convert(boolean)
    def convertString(string: String) = super.convert(string)
  }


  def jValueToCbor(jValue: JValue): Cbor.DataItem = {
    val builder = new StatelessBuilder
    jValue match {
      case JNull => Cbor.SimpleValue.NULL
      case JNothing => Cbor.SimpleValue.UNDEFINED
      case JInt(num) => builder.convertNumber(num)
      case JLong(num) => builder.convertNumber(num)
      case JDecimal(num) => builder.convertNumber(num)
      case JDouble(num) => builder.convertNumber(num)
      case JBool(b) => builder.convertBool(b)
      case JString(s) => builder.convertString(s)

      case JArray(arr) => {
        val cborArr = new Cbor.Array(arr.length)
        arr.foreach(jValue => cborArr.add(jValueToCbor(jValue)))
        cborArr
      }

      case JObject(fields) => {
        val cborMap = new Cbor.Map(fields.length)
        fields.foreach { f: JField =>
          cborMap.put(
            builder.convertString(f._1),
            jValueToCbor(f._2)
          )
        }
        cborMap
      }
    }
  }
}
