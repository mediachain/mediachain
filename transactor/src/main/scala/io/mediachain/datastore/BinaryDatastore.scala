package io.mediachain.datastore

import io.mediachain.multihash.MultiHash
import io.mediachain.types.Datastore._

abstract class BinaryDatastore extends Datastore {
  override def get(ref: Reference): Option[DataObject] = {
    ref match {
      case MultihashReference(multihash) => decode(get(multihash.bytes))
      case _ => None
    }
  }

  def get(key: Array[Byte]): Option[Array[Byte]]
  
  def decode(opt: Option[Array[Byte]]): Option[DataObject] = {
    // deserialize CBOR to DataObject
    throw new RuntimeException("XXX Implement me")
  }
  
  override def put(obj: DataObject): Reference = {
    val data = obj.toCborBytes
    val key = MultiHash.hashWithSHA256(data)
    put(key, data)
    MultihashReference(key)
  }
  
  def put(key: Array[Byte], data: Array[Byte]): Unit

}
