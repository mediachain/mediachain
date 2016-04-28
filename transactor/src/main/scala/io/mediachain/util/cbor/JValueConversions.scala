package io.mediachain.util.cbor


object JValueConversions {
  import co.nstant.in.cbor.CborBuilder
  import co.nstant.in.cbor.{model => Cbor}
  import org.json4s._
  import collection.JavaConverters._

  private implicit class BuilderHead(builder: CborBuilder) {
    def head: Cbor.DataItem =
      builder.build().asScala.headOption.getOrElse {
        throw new MappingException("Unable to convert JValue to CBOR")
      }
  }

  def jValueToCbor(jValue: JValue): Cbor.DataItem =
    jValue match {
      case JInt(num) =>
        new CborBuilder().add(num.bigInteger).head

      case JLong(num) =>
        new CborBuilder().add(num).head

      case JDecimal(num) =>
        new CborBuilder().add(num.doubleValue).head

      case JDouble(num) =>
        new CborBuilder().add(num).head

      case JBool(b) =>
        new CborBuilder().add(b).head

      case JString(s) =>
        new CborBuilder().add(s).head

      case JArray(arr) => {
        val cborValues = arr.map(jValueToCbor)
        val cborArr = new Cbor.Array(cborValues.length)
        cborValues.foreach(cborArr.add)
        cborArr
      }

      case JObject(fields) => {
        val cborMap = new Cbor.Map(fields.length)
        fields.foreach { f: JField =>
          cborMap.put(
            new Cbor.UnicodeString(f._1),
            jValueToCbor(f._2)
          )
        }
        cborMap
      }

      case JNull => Cbor.SimpleValue.NULL

      case JNothing => Cbor.SimpleValue.UNDEFINED
    }
}
