package io.mediachain.transactor

import scala.collection.mutable.{Map => MMap, HashMap => MHashMap}

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
