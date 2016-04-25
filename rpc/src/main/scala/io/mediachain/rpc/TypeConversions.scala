package io.mediachain.rpc

import io.mediachain.Types._
import io.mediachain.rpc.{Types => RPCTypes}

object TypeConversions {

  implicit class CanonicalToRPC(canonical: Canonical) {
    def toRPC: RPCTypes.Canonical = canonicalToRPC(canonical)
  }


  implicit class RPCToCanonical(rpcCanonical: RPCTypes.Canonical) {
    def fromRPC: Canonical = canonicalFromRPC(rpcCanonical)
  }


  implicit class ImageBlobToRPC(imageBlob: ImageBlob) {
    def toRPC: RPCTypes.ImageBlob = imageBlobToRPC(imageBlob)
  }


  implicit class RPCToImageBlob(rpcBlob: RPCTypes.ImageBlob) {
    def fromRPC: ImageBlob = imageBlobFromRPC(rpcBlob)
  }


  implicit class PersonToRPC(person: Person) {
    def toRPC: RPCTypes.Person = personToRPC(person)
  }


  implicit class RPCToPerson(rpcPerson: RPCTypes.Person) {
    def fromRPC: Person = personFromRPC(rpcPerson)
  }


  implicit class RawMetadataBlobToRPC(raw: RawMetadataBlob) {
    def toRPC: RPCTypes.RawMetadataBlob = rawMetadataBlobToRPC(raw)
  }


  implicit class RPCToRawMetadataBlob(rpcRaw: RPCTypes.RawMetadataBlob) {
    def fromRPC: RawMetadataBlob = rawMetadataBlobFromRPC(rpcRaw)
  }


  def canonicalToRPC(canonical: Canonical): RPCTypes.Canonical =
    RPCTypes.Canonical(
      canonicalID = canonical.canonicalID,
      multiHash = canonical.multiHash.base58,
      signatures = canonical.signatures)


  def canonicalFromRPC(rpcCanonical: RPCTypes.Canonical) =
    Canonical(None,
      rpcCanonical.canonicalID,
      signatures = rpcCanonical.signatures)



  def imageBlobToRPC(imageBlob: ImageBlob): RPCTypes.ImageBlob =
    RPCTypes.ImageBlob(
      title = imageBlob.title,
      description = imageBlob.description,
      date = imageBlob.date,
      multiHash = imageBlob.multiHash.base58,
      signatures = imageBlob.signatures,
      externalIds = imageBlob.external_ids)


  def imageBlobFromRPC(rpcBlob: RPCTypes.ImageBlob): ImageBlob =
    ImageBlob(id = None,
      title = rpcBlob.title,
      description = rpcBlob.description,
      date = rpcBlob.date,
      signatures = rpcBlob.signatures,
      external_ids = rpcBlob.externalIds)


  def personToRPC(person: Person): RPCTypes.Person =
    RPCTypes.Person(
      name = person.name,
      multiHash = person.multiHash.base58,
      signatures = person.signatures,
      externalIds = person.external_ids)


  def personFromRPC(rpcPerson: RPCTypes.Person): Person =
    Person(id = None,
      name = rpcPerson.name,
      signatures = rpcPerson.signatures,
      external_ids = rpcPerson.externalIds)


  def rawMetadataBlobToRPC(raw: RawMetadataBlob): RPCTypes.RawMetadataBlob =
    RPCTypes.RawMetadataBlob(
      blob = raw.blob,
      multiHash = raw.multiHash.base58,
      signatures = raw.signatures)

  def rawMetadataBlobFromRPC(rpcRaw: RPCTypes.RawMetadataBlob): RawMetadataBlob =
    RawMetadataBlob(None,
      blob = rpcRaw.blob,
      signatures = rpcRaw.signatures)

  def metadataBlobToRPC(blob: MetadataBlob): RPCTypes.MetadataBlob =
    blob match {
      case image: ImageBlob => RPCTypes.MetadataBlob(
        blobType = RPCTypes.MetadataBlob.BlobType.ImageBlob,
        blob = RPCTypes.MetadataBlob.Blob.Image(image.toRPC))

      case person: Person => RPCTypes.MetadataBlob(
        blobType = RPCTypes.MetadataBlob.BlobType.Person,
        blob = RPCTypes.MetadataBlob.Blob.Person(person.toRPC))

      case raw: RawMetadataBlob => RPCTypes.MetadataBlob(
        blobType = RPCTypes.MetadataBlob.BlobType.RawMetadataBlob,
        blob = RPCTypes.MetadataBlob.Blob.RawMetadata(raw.toRPC))
    }
}
