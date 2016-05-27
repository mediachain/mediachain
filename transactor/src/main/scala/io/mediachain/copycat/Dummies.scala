package io.mediachain.copycat

import scala.collection.mutable.{HashMap => MHashMap, Map => MMap}

object Dummies {
  import io.mediachain.protocol.Datastore._
  import io.mediachain.util.cbor.CborAST._

  class DummyStore extends Datastore {
    var seqno = 0
    val store: MMap[Reference, DataObject] = new MHashMap

    override def put(obj: DataObject): Reference = {
      val ref = DummyReference(seqno)
      seqno += 1
      store += (ref -> obj)
      ref
    }

    override def get(ref: Reference): Option[DataObject] = store.get(ref)
  }

}
