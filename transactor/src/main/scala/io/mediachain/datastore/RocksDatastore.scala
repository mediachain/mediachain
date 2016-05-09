package io.mediachain.datastore

import org.rocksdb._
import io.mediachain.multihash.MultiHash

class RocksDatastore(path: String)
  extends BinaryDatastore with AutoCloseable {
  val db = RocksDB.open(path)
  
  override def put(key: MultiHash, data: Array[Byte]) {
    db.put(key.bytes, data)
  }
  
  override def close() {
    db.close()
  }
  
  override def get(key: MultiHash): Option[Array[Byte]] = 
    Option(db.get(key.bytes))
}

