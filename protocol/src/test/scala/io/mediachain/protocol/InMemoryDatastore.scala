package io.mediachain.protocol

import io.mediachain.protocol.Datastore._

class InMemoryDatastore extends Datastore {
  import collection.mutable.{Map => MMap}

  def copy: InMemoryDatastore = {
    val other = new InMemoryDatastore
    store.foreach(t => other.store.put(t._1, t._2))
    other
  }

  val store: MMap[Reference, DataObject] = MMap()

  override def get(ref: Reference): Option[DataObject] = store.get(ref)

  override def put(obj: DataObject): Reference = {
    val ref = MultihashReference.forDataObject(obj)
    store.put(ref, obj)
    ref
  }
}
