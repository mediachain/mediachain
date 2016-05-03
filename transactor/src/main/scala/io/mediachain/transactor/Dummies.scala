package io.mediachain.transactor

import io.mediachain.hashing.MultiHash

import scala.collection.mutable.{HashMap => MHashMap, Map => MMap}

object Dummies {
  import io.mediachain.transactor.Types._
  import io.mediachain.util.cbor.CborAST._
  
  class DummyReference(val num: Int) extends Reference {
    override def equals(that: Any) = {
      that.isInstanceOf[DummyReference] &&
        this.num == that.asInstanceOf[DummyReference].num
    }
    override def hashCode = num
    override def toString = "dummy@" + num

    val CBORType = "" // unused
    override def toCbor = CMap.withStringKeys("@link" -> CString(this.toString))
  }

  class DummyStore extends Datastore {
    var seqno = 0
    val store: MMap[MultiHash, DataObject] = new MHashMap

    override def put(obj: Array[Byte], multihash: MultiHash): Unit = {}
    override def put(obj: DataObject): MultiHash = {
      val ref = MultiHash.hashWithSHA256(seqno.toString.getBytes)
      seqno += 1
      store += (ref -> obj)
      ref
    }

    def get(ref: MultiHash): Option[DataObject] = store.get(ref)
  }

}
