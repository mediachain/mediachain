package io.mediachain.transactor

import io.mediachain.transactor.CborSerialization.CBORTypeNames


object Types {
  import scala.concurrent.Future
  import cats.data.Xor

  import io.mediachain.transactor.CborSerialization.CborSerializable
  import io.mediachain.util.cbor.CborAST._

  // Base class of all objects storable in the Datastore
  sealed abstract class DataObject extends Serializable with CborSerializable



  // Mediachain Datastore Records
  sealed abstract class Record extends DataObject {
    def meta: Map[String, CValue]
  }

  // References to records in the underlying datastore
  abstract class Reference extends Serializable with CborSerializable

  // Typed References for tracking chain heads in the StateMachine
  sealed abstract class ChainReference extends Serializable {
    def chain: Option[Reference]
  }

  case class EntityChainReference(chain: Option[Reference])
    extends ChainReference

  object EntityChainReference {
    def empty = EntityChainReference(None)
  }

  case class ArtefactChainReference(chain: Option[Reference])
    extends ChainReference

  object ArtefactChainReference {
    def empty = ArtefactChainReference(None)
  }

  // Canonical records: Entities and Artefacts
  sealed abstract class CanonicalRecord extends Record {
    def reference: ChainReference
  }

  case class Entity(
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val CBORType = CBORTypeNames.Entity
    override def toCbor =
      super.toCMapWithDefaults(meta, Map())

    def reference: ChainReference = EntityChainReference.empty
  }
  
  case class Artefact( 
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val CBORType = CBORTypeNames.Artefact
    override def toCbor =
      super.toCMapWithDefaults(meta, Map())
    
    def reference: ChainReference = ArtefactChainReference.empty
  }
  
  // Chain Cells
  sealed abstract class ChainCell extends Record {
    def chain: Option[Reference]
  }
  
  case class EntityChainCell( 
    entity: Reference,
    chain: Option[Reference],
    meta: Map[String, CValue]
  ) extends ChainCell {
    val CBORType = CBORTypeNames.EntityChainCell

    override def toCbor = {
      val defaults = meta + ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }
  
  case class ArtefactChainCell( 
    artefact: Reference,
    chain: Option[Reference],
    meta: Map[String, CValue]
  ) extends ChainCell {
    val CBORType = CBORTypeNames.ArtefactChainCell

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }
  
  // Journal Entries
  sealed abstract class JournalEntry extends Serializable with CborSerializable {
    def index: BigInt
    def ref: Reference
  }
  
  case class CanonicalEntry( 
    index: BigInt,
    ref: Reference
  ) extends JournalEntry {
    val CBORType = CBORTypeNames.CanonicalEntry

    override def toCbor: CValue = {
      val defaults = Map(
        "index" -> CInt(index),
        "ref"   -> ref.toCbor
      )
      super.toCMapWithDefaults(defaults, Map())
    }
  }


  case class ChainEntry( 
    index: BigInt,
    ref: Reference,
    chain: Reference,
    chainPrevious: Option[Reference]
  ) extends JournalEntry {
    val CBORType = CBORTypeNames.ChainEntry

    override def toCbor: CValue = {
      val defaults = Map(
        "index" -> CInt(index),
        "ref"   -> ref.toCbor,
        "chain" -> chain.toCbor
      )
      val prev = chainPrevious.map(_.toCbor)
      val optionals = Map("chainPrevious" -> prev)

      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  // Journal Blocks
  case class JournalBlock(
    index: BigInt,
    chain: Option[Reference],
    entries: Array[JournalEntry]
  ) extends DataObject {
    val CBORType = CBORTypeNames.JournalBlock

    override def toCbor = {
      val cborEntries = CArray(entries.map(_.toCbor).toList)
      val defaults = Map("index" -> CInt(index), "entries" -> cborEntries)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }


  // Journal transactor interface
  trait Journal {
    def insert(rec: CanonicalRecord): Future[Xor[JournalError, CanonicalEntry]]
    def update(ref: Reference, cell: ChainCell): Future[Xor[JournalError, ChainEntry]]
    def lookup(ref: Reference): Future[Option[Reference]]
    def currentBlock: Future[JournalBlock]
  }
  
  trait JournalClient extends Journal {
    def connect(address: String): Unit
    def close(): Unit
    def listen(listener: JournalListener): Unit
  }
  
  trait JournalListener {
    def onJournalCommit(entry: JournalEntry): Unit
    def onJournalBlock(ref: Reference): Unit
  }
  
  sealed abstract class JournalError extends Serializable
  
  case class JournalCommitError(what: String) extends JournalError {
    override def toString = "Journal Commit Error: " + what
  }
  
  // Datastore interface
  trait Datastore {
    def put(obj: DataObject): Reference
  }
}
