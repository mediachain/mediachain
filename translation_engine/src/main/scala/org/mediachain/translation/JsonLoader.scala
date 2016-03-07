package org.mediachain.translation

import org.mediachain.translation.TranslationError.{ResourceNotReadable, ParsingFailed}


object JsonLoader {

  import scala.io.Source
  import java.io.File
  import java.net.{URI, URL}
  import cats.data.Xor
  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  def loadObjectFromString(jsonString: String): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(parse(jsonString).asInstanceOf[JObject])
        .leftMap(ParsingFailed)
  }

  def loadObjectFromSource(source: Source): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(source.mkString)
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromString)
  }

  def loadObjectFromURI(uri: URI): Xor[TranslationError, JObject] =
    loadObjectFromURL(uri.toURL)

  def loadObjectFromURL(url: URL): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(Source.fromURL(url))
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromSource)
  }

  def loadObjectFromFile(file: File): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(Source.fromFile(file))
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromSource)
  }


  def loadObjectsFromDirectoryTree(directory: File, fileExtension: String = ".json")
  : Vector[Xor[TranslationError, JObject]] = {
    val files = DirectoryWalker.findWithExtension(directory, fileExtension)
    files.map(loadObjectFromFile)
  }

}
