package io.mediachain.transactor

object Types {
  import cats.data.Xor
  import org.json4s.JValue

  // Mediachain Datastore Records
  abstract class Record {
    def meta: Map[String, JValue]
  }
  
  // References to records in the underlying datastore
  abstract class Reference
  
  // Canonical records: Entities and Artefacts
  abstract class CanonicalRecord extends Record
  
  case class Entity(
    meta: Map[String, JValue]
  ) extends CanonicalRecord
  
  case class Artefact( 
    meta: Map[String, JValue]
  ) extends CanonicalRecord
  
  // Chain Cells
  abstract class ChainCell extends Record {
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
