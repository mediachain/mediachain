package io.mediachain.transactor

import io.mediachain.transactor.TypeSerialization.CBORTypeNames


object Types {
  import cats.data.Xor

  import io.mediachain.util.cbor.CborCodec
  import io.mediachain.util.cbor.CborAST._

  // Base class of all objects storable in the Datastore
  sealed abstract class DataObject extends Serializable with ToCbor

  trait ToCbor {
    val CBORType: String

    def toCborBytes: Array[Byte] = CborCodec.encode(toCbor)

    def toCbor: CValue =
      toCMapWithDefaults(Map.empty, Map.empty)

    def toCMapWithDefaults(defaults: Map[String, CValue],
                           optionals: Map[String, Option[CValue]])
    : CMap = {
      val merged = defaults ++ optionals.flatMap {
        case (_, None) => List.empty
        case (k, Some(v)) => List(k -> v)
      }
      val withType = ("type", CString(CBORType)) :: merged.toList

      CMap.withStringKeys(withType)
    }
  }

  // Mediachain Datastore Records
  sealed abstract class Record extends DataObject {
    def meta: Map[String, CValue]
  }

  // References to records in the underlying datastore
  abstract class Reference extends Serializable with ToCbor

  // Typed References for tracking chain heads in the StateMachine
  sealed abstract class ChainReference extends Serializable with ToCbor {
    def chain: Option[Reference]

    override def toCbor =
      toCMapWithDefaults(Map(), Map("chain" -> chain.map(_.toCbor)))
  }

  case class EntityChainReference(chain: Option[Reference])
    extends ChainReference {
    val CBORType = CBORTypeNames.EntityChainReference
  }

  object EntityChainReference {
    def empty = EntityChainReference(None)
  }

  case class ArtefactChainReference(chain: Option[Reference])
    extends ChainReference {
    val CBORType = CBORTypeNames.ArtefactChainReference
  }

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
      super.toCMapWithDefaults(meta + ("reference" -> reference.toCbor), Map())

    def reference: ChainReference = EntityChainReference.empty
  }
  
  case class Artefact( 
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val CBORType = CBORTypeNames.Artefact
    override def toCbor =
      super.toCMapWithDefaults(meta + ("reference" -> reference.toCbor), Map())
    
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
  sealed abstract class JournalEntry extends DataObject
    with Serializable with ToCbor {
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
    def insert(rec: CanonicalRecord): Xor[JournalError, CanonicalEntry]
    def update(ref: Reference, cell: ChainCell): Xor[JournalError, ChainEntry]
    def lookup(ref: Reference): Option[Reference]
    def currentBlock: JournalBlock
  }
  
  trait JournalClient {
    def updateJournal(entry: JournalEntry): Unit
    def updateJournalBlockchain(ref: Reference): Unit
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
