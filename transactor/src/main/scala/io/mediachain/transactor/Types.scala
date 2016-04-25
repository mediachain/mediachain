package io.mediachain.transactor

object Types {
  import cats.data.Xor
  
  // Mediachain Datastore Records
  abstract class Record
  
  // References to records in the underlying datastore
  abstract class Reference
  
  // Canonical records: Entities and Artefacts
  abstract class CanonicalRecord extends Record {
    def name: String
    def meta: Map[String, Any]
  }
  
  case class Entity(
    name: String, 
    meta: Map[String, Any]
  ) extends CanonicalRecord
  
  case class Artefact( 
    name: String,
    meta: Map[String, Any]
  ) extends CanonicalRecord
  
  // Chain Cells
  abstract class ChainCell extends Record {
    def chain: Option[Reference]
    def meta: Map[String, Any]
  }
  
  abstract class EntityChainCell extends ChainCell {
    def entity: Reference
  }
  
  abstract class ArtefactChainCell extends ChainCell {
    def artefact: Reference
  }
  
  // Journal Entries
  abstract class JournalEntry {
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
  
  abstract class JournalError
}
