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


  def canonicalToRPC(canonical: Canonical): RPCTypes.Canonical =
    RPCTypes.Canonical(canonicalID = canonical.canonicalID)


  def canonicalFromRPC(rpcCanonical: RPCTypes.Canonical) =
    Canonical(None, rpcCanonical.canonicalID)



  def imageBlobToRPC(imageBlob: ImageBlob): RPCTypes.ImageBlob =
    RPCTypes.ImageBlob(
      title = imageBlob.title,
      description = imageBlob.description,
      date = imageBlob.date
    )


  def imageBlobFromRPC(rpcBlob: RPCTypes.ImageBlob): ImageBlob =
    ImageBlob(id = None,
      title = rpcBlob.title,
      description = rpcBlob.description,
      date = rpcBlob.date)


  def personToRPC(person: Person): RPCTypes.Person =
    RPCTypes.Person(name = person.name)


  def personFromRPC(rpcPerson: RPCTypes.Person): Person =
    Person(id = None, name = rpcPerson.name)

}
