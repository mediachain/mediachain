package io.mediachain.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.Module.SetupContext
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.ser.Serializers
import org.json4s._
import org.json4s.jackson.Json4sScalaModule

class SortedObjectSerializer extends JsonSerializer[JValue]{
  def serialize(value: JValue, json: JsonGenerator, provider: SerializerProvider) {
    if (value == null) {
      json.writeNull()
    } else {
      value match {
        case JInt(v) => json.writeNumber(v.bigInteger)
        case JLong(v) => json.writeNumber(v)
        case JDouble(v) => json.writeNumber(v)
        case JDecimal(v) => json.writeNumber(v.bigDecimal)
        case JString(v) => json.writeString(v)
        case JBool(v) => json.writeBoolean(v)
        case JArray(elements) =>
          json.writeStartArray()
          elements filterNot (_ == JNothing) foreach (x => serialize(x, json, provider))
          json.writeEndArray()

        case JObject(fields) => {
          json.writeStartObject()
          fields sortWith { (f1, f2) =>
            f1._1.compareTo(f2._1) < 0
          } filterNot (_._2 == JNothing) foreach {
            case (n, v) =>
              json.writeFieldName(n)
              serialize(v, json, provider)
          }
          json.writeEndObject()
        }
        case JNull => json.writeNull()
        case JNothing => ()
      }
    }
  }

  override def isEmpty(value: JValue): Boolean = value == JNothing
}

private object SortedObjectSerializerResolver extends Serializers.Base {
  private val JVALUE = classOf[JValue]
  override def findSerializer(config: SerializationConfig, theType: JavaType, beanDesc: BeanDescription) = {
    if (!JVALUE.isAssignableFrom(theType.getRawClass)) null
    else new SortedObjectSerializer
  }
}


class Json4sWithSortedObjectsScalaModule extends Json4sScalaModule {
  override def setupModule(ctxt: SetupContext) {
    super.setupModule(ctxt)
    ctxt.addSerializers(SortedObjectSerializerResolver)
  }
}