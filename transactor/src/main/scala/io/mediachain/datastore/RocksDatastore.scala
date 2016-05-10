package io.mediachain.datastore

import org.rocksdb._
import scala.collection.mutable.ListBuffer
import io.mediachain.multihash.MultiHash

class RocksDatastore(path: String)
  extends BinaryDatastore with AutoCloseable {
  val db = RocksDB.open(path)
  
  override def putData(key: MultiHash, data: Array[Byte]) {
    db.put(key.bytes, data)
  }
  
  override def close() {
    db.close()
  }
  
  override def getData(key: MultiHash): Option[Array[Byte]] = 
    Option(db.get(key.bytes))
  
  def removeData(key: MultiHash) {
    db.remove(key.bytes)
  }
  
  def getKeys(): List[MultiHash] = {
    val keys = new ListBuffer[MultiHash]
    val iter = db.newIterator
    iter.seekToFirst
    while (iter.isValid) {
      MultiHash.fromBytes(iter.key).foreach {key => keys += key}
      iter.next
    }
    keys.toList
  }
}

