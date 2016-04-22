package io.mediachain.api.operations

import cats.data.Xor
import io.mediachain.BlobBundle
import io.mediachain.core.TranslationError

object Translation {

  // TODO: make sure returned blobs include hashes
  case class TranslationOutput(translated: BlobBundle)

  def translateRawMetadata(partnerName: String, rawMetadataString: String):
    Xor[TranslationError, TranslationOutput] = ???


}
