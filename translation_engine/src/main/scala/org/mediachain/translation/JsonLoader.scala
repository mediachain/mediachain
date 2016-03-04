package org.mediachain.translation



object JsonLoader {

  import scala.io.Source
  import java.io.File
  import java.net.{URI, URL}
  import cats.data.Xor
  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  def loadObjectFromString(jsonString: String): Xor[Throwable, JObject] = {
    Xor.catchNonFatal(parse(jsonString).asInstanceOf[JObject])
  }

  def loadObjectFromSource(source: Source): Xor[Throwable, JObject] = {
    Xor.catchNonFatal(source.mkString)
      .flatMap(loadObjectFromString)
  }

  def loadObjectFromURI(uri: URI): Xor[Throwable, JObject] =
    loadObjectFromURL(uri.toURL)

  def loadObjectFromURL(url: URL): Xor[Throwable, JObject] = {
    Xor.catchNonFatal(Source.fromURL(url))
      .flatMap(loadObjectFromSource)
  }

  def loadObjectFromFile(file: File): Xor[Throwable, JObject] = {
    Xor.catchNonFatal(Source.fromFile(file))
      .flatMap(loadObjectFromSource)
  }


  def loadObjectsFromDirectoryTree(directory: File, fileExtension: String = ".json")
  : Vector[Xor[Throwable, JObject]] = {
    val files = DirectoryWalker.findWithExtension(directory, fileExtension)
    files.map(loadObjectFromFile)
  }

}
