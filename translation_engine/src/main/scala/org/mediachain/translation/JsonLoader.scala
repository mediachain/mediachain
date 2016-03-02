package org.mediachain.translation



object JsonLoader {

  import java.net.URL
  import cats.data.Xor
  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  def loadObjectFromString(jsonString: String): Xor[Throwable, JObject] = {
    Xor.catchNonFatal {
      parse(jsonString).asInstanceOf[JObject]
    }
  }

  def loadObjectFromURL(url: URL): Xor[Throwable, JObject] = {
    Xor.catchNonFatal {
      import scala.io.Source
      val source = Source.fromURL(url)
      val contents = source.mkString
      source.close()
      contents
    }.flatMap(loadObjectFromString)
  }

}
