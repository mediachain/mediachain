package io.mediachain.types

// Datastore interface
trait Datastore {
  import DatastoreTypes.{DataObject, Reference}

  def put(obj: DataObject): Reference
}