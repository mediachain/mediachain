package io.mediachain.util.datastore

import org.rocksdb._
import io.mediachain.transactor.Types.{DataObject, Datastore}
import io.mediachain.hashing.MultiHash

class RocksDatastore(path: String) extends Datastore with AutoCloseable {
  val db = RocksDB.open(path)

  override def put(obj: Array[Byte], hash: MultiHash): Unit = {
    db.put(hash.bytes, obj)
  }

  override def get(ref: MultiHash): Option[DataObject] = {
    val bytes = db.get(ref.bytes)
    ???
  }

  override def close: Unit = {
    db.close()
  }
}

