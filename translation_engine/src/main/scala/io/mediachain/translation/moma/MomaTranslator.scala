package io.mediachain.translation.moma

import cats.data.Xor
import io.mediachain.BlobBundle
import io.mediachain.core.TranslationError
import io.mediachain.core.TranslationError.ParsingFailed
import org.json4s._
import io.mediachain.Types.{ImageBlob, Person}
import io.mediachain.translation.{FlatFileLoader, Translator}

import scala.util.{Failure, Success, Try}

object MomaTranslator extends Translator {
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
  private case class MomaImageBlob(MoMANumber: String,
                                   Title: String,
                           Medium: String,
                           Date: String,
                           Artist: String) {
    def asBlobBundle: BlobBundle = {
      val image = ImageBlob(
        id = None,
        title = Title,
        description = Medium,
        date = Date,
        external_ids = Map("moma:MoMANumber" -> MoMANumber)
      )

      BlobBundle(image, BlobBundle.Author(
        Person(None, Artist)
      ))
    }
  }

  /** Casts a JValue to a ImageBlob using `extract` and `asImageBlob`
    *
    * @param json The JValue representing a work
    * @return A `ImageBlob` extracted from the JValue
    */
  def translate(json: JObject): Xor[TranslationError, BlobBundle] = {
    implicit val formats = DefaultFormats
    Try(json.extract[MomaImageBlob].asBlobBundle) match {
      case Success(blob) => Xor.right(blob)
      case Failure(exn)  => Xor.left(ParsingFailed(exn))
    }
  }
}

class MomaLoader(val path: String, implicit val translator: MomaTranslator.type = MomaTranslator) extends FlatFileLoader[MomaTranslator.type]

