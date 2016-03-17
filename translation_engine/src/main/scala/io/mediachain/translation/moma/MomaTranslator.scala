package io.mediachain.translation.moma

import java.io.File

import cats.data.Xor
import io.mediachain.translation.TranslationError.ParsingFailed
import org.json4s._

import io.mediachain.Types.{Person, PhotoBlob}

import io.mediachain.translation.{FlatFileLoader, TranslationError, Translator}

import scala.util.{Try, Success, Failure}

trait MomaTranslator extends Translator {
  val name = "MomaCollectionTranslator"
  val version = 1

  implicit val formats = org.json4s.DefaultFormats

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
  def translate(json: JObject): Xor[TranslationError, PhotoBlob] = {
    //implicit val formats = DefaultFormats
    //val rawBlob = RawMetadataBlob(None, write(json))
    Try(json.extract[MomaPhotoBlob].asPhotoBlob) match {
      case Success(blob) => Xor.right(blob)
      case Failure(exn)  => Xor.left(ParsingFailed(exn))
    }
  }

  /** Given a filename representing a MoMA-schema list of artworks, parse the
    * file into a stream of `PhotoBlob`s.
    *
    * @param path The filename to read and parse
    * @return A stream of `PhotoBlob`s
    */
}

class MomaLoader(val path: String) extends MomaTranslator with FlatFileLoader

