package io.mediachain.translation

import cats.data.Xor
import io.mediachain.Types.{RawMetadataBlob, MetadataBlob}

trait TranslationContext[BlobType <: MetadataBlob] {
  val id: String
  val translator: Translator

  def translate(source: String): Xor[TranslationError, (BlobType, RawMetadataBlob)]
}
