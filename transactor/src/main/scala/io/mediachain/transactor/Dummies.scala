package io.mediachain.transactor

import scala.collection.mutable.{HashMap => MHashMap, Map => MMap}

object Dummies {
  import io.mediachain.protocol.Datastore._
  import io.mediachain.util.cbor.CborAST._

  class DummyReference(val num: Int) extends Reference {
    override def equals(that: Any) = {
      that.isInstanceOf[DummyReference] && 
        this.num == that.asInstanceOf[DummyReference].num
    }
    override def hashCode = num
    override def toString = "dummy@" + num

    // CBOR serialization is not used for dummy references,
    // but having the base Reference type implement CborSerializable
    // greatly simplifies the serialization logic for objects that contain
    // references.
    val mediachainType = None
    override def toCbor = CMap.withStringKeys("@dummy-link" -> CInt(num))
  }

  class DummyStore extends Datastore {
    var seqno = 0
    val store: MMap[Reference, DataObject] = new MHashMap
    
    override def put(obj: DataObject): Reference = {
      val ref = new DummyReference(seqno)
      seqno += 1
      store += (ref -> obj)
      ref
    }
    
    def get(ref: Reference) = store.get(ref)
  }

}
