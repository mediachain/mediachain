package io.mediachain.util.datastore

import org.rocksdb._
import io.mediachain.transactor.Types.{DataObject, MultiHashDatastore, MultihashReference}
import io.mediachain.multihash.MultiHash

class RocksDatastore(path: String)
  extends MultiHashDatastore with AutoCloseable {
  val db = RocksDB.open(path)

  override def put(obj: Array[Byte], hash: MultiHash): Unit = {
    db.put(hash.bytes, obj)
  }

  override def put(obj: DataObject): Ref = {
    val bytes = obj.toCborBytes
    val hash = MultiHash.hashWithSHA256(bytes)
    db.put(hash.bytes, bytes)
    MultihashReference(hash)
  }

  override def get(ref: MultiHash): Option[DataObject] = {
    val bytes = db.get(ref.bytes)
    ???
  }

  override def close: Unit = {
    db.close()
  }
}

