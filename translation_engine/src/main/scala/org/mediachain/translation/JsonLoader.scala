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


  def loadObjectFromURL(url: URL): Xor[Throwable, JObject] = {
    for {
      source <- Xor.catchNonFatal(Source.fromURL(url))
      obj <- loadObjectFromSource(source)
    } yield obj
  }

  def loadObjectFromFile(file: File): Xor[Throwable, JObject] = {
    for {
      source <- Xor.catchNonFatal(Source.fromFile(file))
      obj <- loadObjectFromSource(source)
    } yield obj
  }

  /*
  Doesn't compile :(

  def loadObjectsFromDirectoryTree(directoryURI: URI): Xor[Throwable, Seq[JObject]] = {
    val dir = new File(directoryURI)
    for {
      file <- DirectoryWalker.findWithExtension(dir, ".json")
      obj <- loadObjectFromFile(file)
    } yield obj
  }

  */
}
