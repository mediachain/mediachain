package io.mediachain.transactor


object Types {
  import cats.data.Xor

  import io.mediachain.util.cbor._
  import org.json4s.{JValue, JObject, JString}
  import io.mediachain.util.cbor.JValueConversions.jValueToCbor

  // Base class of all objects storable in the Datastore
  sealed abstract class DataObject extends Serializable

  trait ToJObject {
    val CBORType: String

    def toCbor: CValue = jValueToCbor(toJObject)
    def toCborBytes: Array[Byte] = encode(toCbor)

    def toJObject: JObject =
      toJObjectWithDefaults(Map.empty, Map.empty)

    def toJObjectWithDefaults(defaults: Map[String, JValue],
                              optionals: Map[String, Option[JValue]]):
    JObject = {
      val merged = defaults ++ optionals.flatMap {
        case (_, None) => List.empty
        case (k, Some(v)) => List(k -> v)
      }
      val withType = ("type", JString(CBORType)) :: merged.toList

      JObject(withType)
    }
  }

  // Mediachain Datastore Records
  sealed abstract class Record extends DataObject {
    def meta: Map[String, JValue]
  }
  
  // References to records in the underlying datastore
  abstract class Reference extends Serializable

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
  sealed abstract class JournalEntry extends Serializable {
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
  
  // Journal Blocks
  case class JournalBlock(
    index: BigInt,
    chain: Option[Reference],
    entries: Array[JournalEntry]
  ) extends DataObject
  
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
