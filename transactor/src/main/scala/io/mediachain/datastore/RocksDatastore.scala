package io.mediachain.datastore

import org.rocksdb._

class RocksDatastore(path: String)
  extends BinaryDatastore with AutoCloseable {
  val db = RocksDB.open(path)
  
  override def put(key: Array[Byte], data: Array[Byte]) {
    db.put(key, data)
  }
  
  override def close() {
    db.close()
  }
  
  override def get(key: Array[Byte]): Option[Array[Byte]] = 
    Option(db.get(key))
}

