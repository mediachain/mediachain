package io.mediachain.util.datastore

import org.rocksdb._
import io.mediachain.transactor.Types.{DataObject, Datastore, Reference}
import io.mediachain.util.cbor.CborAST.{CString, CValue}
import io.mediachain.hashing.MultiHash

class RocksDatastore(path: String) extends Datastore with AutoCloseable {
  import RocksDatastore.RocksReference

  val db = RocksDB.open(path)

  override def put(obj: DataObject): Reference = {
    val bytes = obj.toCborBytes
    val hash  = MultiHash.hashWithSHA256(bytes)
    val ref   = RocksReference(hash.bytes)

    db.put(ref.id, bytes)

    ref
  }

  override def close: Unit = {
    db.close()
  }
}

object RocksDatastore {
  class RocksReference(val id: Array[Byte]) extends Reference {
    override val CBORType: String = ""
    override def toCbor: CValue = CString(new String(id))

    override def toString(): String =
      MultiHash.fromBytes(id)
        .map(_.toString)
        .getOrElse("RocksReference[Invalid reference]")

    override def hashCode(): Int = id.hashCode

    override def equals(other: Any): Boolean = other match {
      case (that: RocksReference) => that.id sameElements id
      case _ => false
    }
  }

  object RocksReference {
    def apply(id: Array[Byte]) = new RocksReference(id)
  }
}
