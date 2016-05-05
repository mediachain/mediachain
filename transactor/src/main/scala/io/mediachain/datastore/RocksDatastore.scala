package io.mediachain.datastore

import org.rocksdb._
import io.mediachain.transactor.Types.{DataObject, MultiHashDatastore, MultihashReference}
import io.mediachain.multihash.MultiHash

class RocksDatastore(path: String)
  extends MultiHashDatastore with AutoCloseable {
  val db = RocksDB.open(path)

  override def put(obj: DataObject): Ref = {
    val bytes = obj.toCborBytes
    val hash = MultiHash.hashWithSHA256(bytes)
    db.put(hash.bytes, bytes)
    MultihashReference(hash)
  }

  override def close: Unit = {
    db.close()
  }

  override def get(ref: MultihashReference): Option[DataObject] = {
    val result = db.get(ref.multihash.bytes)

    Option(result).flatMap { bytes =>
      // here we need to deserialize using yusef's chg
      ???
    }
  }
}

