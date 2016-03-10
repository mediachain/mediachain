package org.mediachain.io

import java.lang.Iterable
import java.security.MessageDigest
import java.util.Base64

import com.fasterxml.jackson.databind.Module.SetupContext
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig
import com.fasterxml.jackson.databind.ser.{SerializerFactory, Serializers, BasicSerializerFactory}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.dataformat.cbor.{CBORGenerator, CBORFactory}
import org.json4s._
import org.json4s.jackson.{JValueDeserializerResolver, JValueSerializerResolver, Json4sScalaModule}

import scala.util.control.Exception._

class CborModule extends Json4sScalaModule {
  override def setupModule(ctxt: SetupContext) {
    super.setupModule(ctxt)
    ctxt.addSerializers(SortedObjectSerializerResolver)
  }
}

object CborMethods extends org.json4s.JsonMethods[JValue] {
  private[this] lazy val _defaultMapper = {
    val f = new CBORFactory()
    f.configure(CBORGenerator.Feature.WRITE_MINIMAL_INTS, true)

    val m = new ObjectMapper(f)
    m.registerModule(new CborModule)
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

  def sha1String(d: JValue): String = {
    val md = MessageDigest.getInstance("SHA-1")
    md.digest(bytes(d)).map("%02x".format(_)).mkString
  }
}
