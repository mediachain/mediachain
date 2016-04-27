package io.mediachain.transactor

object Types {
  import cats.data.Xor
  import org.json4s.JValue

  // Mediachain Datastore Records
  sealed abstract class Record {
    def meta: Map[String, JValue]
  }
  
  // References to records in the underlying datastore
  abstract class Reference

  // Typed References for tracking chain heads in the StateMachine
  sealed abstract class ChainReference {
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
    meta: Map[String, JValue]
  ) extends CanonicalRecord {
    def reference: ChainReference = EntityChainReference.empty
  }
  
  case class Artefact( 
    meta: Map[String, JValue]
  ) extends CanonicalRecord {
    def reference: ChainReference = ArtefactChainReference.empty
  }
  
  // Chain Cells
  sealed abstract class ChainCell extends Record {
    def chain: Option[Reference]
  }
  
  case class EntityChainCell( 
    entity: Reference,
    chain: Option[Reference],
    meta: Map[String, JValue]
  ) extends ChainCell
  
  case class ArtefactChainCell( 
    artefact: Reference,
    chain: Option[Reference],
    meta: Map[String, JValue]
  ) extends ChainCell
  
  // Journal Entries
  sealed abstract class JournalEntry {
    def index: BigInt
    def ref: Reference
  }
  
  case class CanonicalEntry( 
    index: BigInt,
    ref: Reference
  ) extends JournalEntry
  
  case class ChainEntry( 
    index: BigInt,
    ref: Reference,
    chain: Reference,
    chainPrevious: Option[Reference]
  ) extends JournalEntry
  
  // Journal transactor interface
  trait Journal {
    def insert(rec: CanonicalRecord): Xor[JournalError, CanonicalEntry]
    def update(ref: Reference, cell: ChainCell): Xor[JournalError, ChainEntry]
    def lookup(ref: Reference): Option[Reference]
  }
  
  trait JournalClient {
    def updateJournal(entry: JournalEntry): Unit
  }
  
  sealed abstract class JournalError
  
  case class JournalCommitError(what: String) extends JournalError {
    override def toString = "Journal Commit Error: " + what
  }
  
  // Datastore interface
  trait Datastore {
    def put(rec: Record): Reference
  }
}
