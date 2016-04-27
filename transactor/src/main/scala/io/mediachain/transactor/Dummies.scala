package io.mediachain.transactor

import scala.collection.mutable.{Map => MMap, HashMap => MHashMap}

object Dummies {
  import io.mediachain.transactor.Types._

  case class DummyReference(num: Int) extends Reference {
    override def equals(that: Any) = {
      that.isInstanceOf[DummyReference] && 
        this.num == that.asInstanceOf[DummyReference].num
    }
    override def hashCode = num
    override def toString = "dummy@" + num
  }
  
  class DummyStore extends Datastore {
    var seqno = 0
    val records: MMap[Reference, Record] = new MHashMap
    
    override def put(rec: Record): Reference = {
      val ref = new DummyReference(seqno)
      seqno += 1
      records += (ref -> rec)
      ref
    }
  }

}
