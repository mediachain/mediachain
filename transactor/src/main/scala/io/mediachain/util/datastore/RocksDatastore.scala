package io.mediachain.util.datastore

import org.rocksdb._
import io.mediachain.transactor.Types.{DataObject, Datastore, Reference}
import io.mediachain.util.cbor.CborAST.{CString, CValue}
import io.mediachain.hashing.MultiHash
import io.mediachain.transactor.TypeSerialization

class RocksDatastore(path: String) extends Datastore {
  import RocksDatastore.RocksReference

  val db = RocksDB.open(path)

  override def put(obj: DataObject): Reference = {
    val bytes = obj.toCborBytes
    val hash  = MultiHash.hashWithSHA256(bytes)
    val ref   = RocksReference(hash.bytes)

    db.put(ref.id, bytes)

    ref
  }
}

object RocksDatastore {
  class RocksReference(val id: Array[Byte]) extends Reference {
    override val CBORType: String = ""
    override def toCbor: CValue = CString(new String(id))
  }

  object RocksReference {
    def apply(id: Array[Byte]) = new RocksReference(id)
  }
}
