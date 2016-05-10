package io.mediachain.datastore

import org.rocksdb._
import scala.collection.mutable.ListBuffer
import io.mediachain.multihash.MultiHash

class RocksDatastore(path: String)
  extends BinaryDatastore with AutoCloseable {
  val db = RocksDB.open(path)
  
  override def putData(key: MultiHash, data: Array[Byte]) {
    this.synchronized {db.put(key.bytes, data)}
  }
  
  override def close() {
    this.synchronized {db.close()}
  }
  
  override def getData(key: MultiHash): Option[Array[Byte]] = 
    this.synchronized {Option(db.get(key.bytes))}
  
  def removeData(key: MultiHash) {
    this.synchronized {db.remove(key.bytes)}
  }
  
  def getKeys(): List[MultiHash] = 
    this.synchronized {
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

