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

  def canonicalToRPC(canonical: Canonical): RPCTypes.Canonical = {
    RPCTypes.Canonical(canonicalID = canonical.canonicalID)
  }

  def canonicalFromRPC(rpcCanonical: RPCTypes.Canonical) = {
    Canonical(None, rpcCanonical.canonicalID)
  }


  def imageBlobToRPC(imageBlob: ImageBlob): RPCTypes.ImageBlob = {
    RPCTypes.ImageBlob(
      title = imageBlob.title,
      description = imageBlob.description,
      date = imageBlob.date
    )
  }

  def personToRPC(person: Person): RPCTypes.Person = {
    RPCTypes.Person(name = person.name)
  }
}
