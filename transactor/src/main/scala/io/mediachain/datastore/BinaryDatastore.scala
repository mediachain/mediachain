package io.mediachain.datastore

import cats.data.Xor
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization

abstract class BinaryDatastore extends Datastore {
  override def get(ref: Reference): Option[DataObject] = {
    ref match {
      case MultihashReference(multihash) => decode(getData(multihash))
      case _ => None
    }
  }

  def getData(key: MultiHash): Option[Array[Byte]]
  
  def decode(opt: Option[Array[Byte]]): Option[DataObject] = {
    opt.map { bytes =>
      CborSerialization.dataObjectFromCborBytes(bytes) match {
        case Xor.Right(obj) => obj
        case Xor.Left(err) => {
          // this is probably indicative of a bug, so throw
          throw new DatastoreException("Object deserialization error: " + err.message)
        }
      }
    }
  }
  
  override def put(obj: DataObject): Reference = {
    val data = obj.toCborBytes
    val key = MultiHash.hashWithSHA256(data)
    putData(key, data)
    MultihashReference(key)
  }
  
  def putData(key: MultiHash, data: Array[Byte]): Unit

}
