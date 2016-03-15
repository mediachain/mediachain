package org.mediachain.io

import java.util.Base64

import com.fasterxml.jackson.databind.{JsonNode, DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import org.json4s._
import org.json4s.jackson.{Json4sScalaModule, JsonMethods}

import scala.util.control.Exception._


object CborMethods extends org.json4s.JsonMethods[JValue] {
  private[this] lazy val _defaultMapper = {
    val m = new ObjectMapper(new CBORFactory())
    m.registerModule(new Json4sScalaModule)
    m
  }
  def mapper = _defaultMapper

  def parse(in: JsonInput, useBigDecimalForDouble: Boolean = false): JValue = {
    // What about side effects?
    mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, useBigDecimalForDouble)
    in match {
      case StringInput(s) =>
        val bytes = Base64.getDecoder.decode(s)
        mapper.readValue(bytes, classOf[JValue])
      case ReaderInput(rdr) => mapper.readValue(rdr, classOf[JValue])
      case StreamInput(stream) => mapper.readValue(stream, classOf[JValue])
      case FileInput(file) => mapper.readValue(file, classOf[JValue])
    }
  }

  def parseOpt(in: JsonInput, useBigDecimalForDouble: Boolean = false): Option[JValue] =  allCatch opt {
    parse(in, useBigDecimalForDouble)
  }

  def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  def asJValue[T](obj: T)(implicit writer: Writer[T]): JValue = writer.write(obj)
  def fromJValue[T](json: JValue)(implicit reader: Reader[T]): T = reader.read(json)

  def asJsonNode(jv: JValue): JsonNode = mapper.valueToTree[JsonNode](jv)
  def fromJsonNode(jn: JsonNode): JValue = mapper.treeToValue[JValue](jn, classOf[JValue])


  def bytes(d: JValue): Array[Byte] = {
    mapper.writeValueAsBytes(d)
  }

  def compact(d: JValue): String = {
    Base64.getEncoder.encodeToString(bytes(d))
  }

  def pretty(d: JValue): String = compact(d)

}
