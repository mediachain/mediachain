package io.mediachain.util.cbor


object JValueConversions {
  import co.nstant.in.cbor.CborBuilder
  import co.nstant.in.cbor.{model => Cbor}
  import org.json4s._
  import collection.JavaConverters._

  private def firstItem(builder: CborBuilder): Cbor.DataItem =
    builder.build().asScala.headOption.getOrElse {
    throw new MappingException("Unable to convert JValue to CBOR")
  }

  def jValueToCbor(jValue: JValue): Cbor.DataItem =
    jValue match {
      case JInt(num) => firstItem {
        new CborBuilder().add(num.bigInteger)
      }

      case JLong(num) => firstItem {
        new CborBuilder().add(num)
      }

      case JDecimal(num) => firstItem {
        new CborBuilder().add(num.doubleValue())
      }

      case JDouble(num) => firstItem {
        new CborBuilder().add(num)
      }

      case JBool(b) => firstItem {
        new CborBuilder().add(b)
      }

      case JString(s) => firstItem {
        new CborBuilder().add(s)
      }

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
