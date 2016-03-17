package io.mediachain.translation.moma

import java.io.File

import cats.data.{Streaming, Xor}
import com.fasterxml.jackson.core.JsonFactory
import io.mediachain.translation.TranslationError.ParsingFailed
import org.json4s._
import org.json4s.jackson.Serialization.write
import io.mediachain.Types.{RawMetadataBlob, Person, PhotoBlob}
import io.mediachain.translation.JsonLoader.parseJArray
import io.mediachain.translation.{TranslationError, Translator}

import scala.util.{Try, Success, Failure}

object MomaTranslator extends Translator {
  val name = "MomaCollectionTranslator"
  val version = 1

  /** A convenience case class for parsing JSON blobs into a more abstract form.
    *
    * @param Title Title of the work
    * @param Medium Medium of the work (becomes description for now)
    * @param Date Date of the work
    * @param Artist Artist of the work
    */
  private case class MomaPhotoBlob(Title: String,
                           Medium: String,
                           Date: String,
                           Artist: String) {
    def asPhotoBlob: PhotoBlob = PhotoBlob(
      id = None,
      title = Title,
      description = Medium,
      date = Date,
      author = Some(Person(id = None, Artist))
    )
  }

  /** Casts a JValue to a PhotoBlob using `extract` and `asPhotoBlob`
    *
    * @param json The JValue representing a work
    * @return A `PhotoBlob` extracted from the JValue
    */
  def jsonToPhotoBlobTuple(json: JObject): Xor[TranslationError, (PhotoBlob, RawMetadataBlob)] = {
    implicit val formats = DefaultFormats
    val rawBlob = RawMetadataBlob(None, write(json))
    Try(json.extract[MomaPhotoBlob].asPhotoBlob) match {
      case Success(blob) => Xor.right((blob, rawBlob))
      case Failure(exn)  => Xor.left(ParsingFailed(exn))
    }
  }

  /** Given a filename representing a MoMA-schema list of artworks, parse the
    * file into a stream of `PhotoBlob`s.
    *
    * @param path The filename to read and parse
    * @return A stream of `PhotoBlob`s
    */
  def loadPhotoBlobs(path: String): Iterator[Xor[TranslationError, (PhotoBlob, RawMetadataBlob)]] = {
    val jf = new JsonFactory
    val parser = jf.createParser(new File(path))

    parser.nextToken

    parseJArray(parser).flatMap {
      case Xor.Right(json: JObject) => List(jsonToPhotoBlobTuple(json))
      case _ => List.empty
    }
  }
}

