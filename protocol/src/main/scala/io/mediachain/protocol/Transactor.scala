package io.mediachain.protocol

import cats.data.Xor

import scala.concurrent.Future


object Transactor {
  import Datastore._

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

  // Journal transactor interface
  trait Journal {
    def insert(rec: CanonicalRecord): Future[Xor[JournalError, CanonicalEntry]]
    def update(ref: Reference, cell: ChainCell): Future[Xor[JournalError, ChainEntry]]
    def lookup(ref: Reference): Future[Xor[JournalError, Option[Reference]]]
    def currentBlock: Future[JournalBlock]
  }

  trait JournalClient extends Journal {
    def connect(cluster: List[String]): Unit
    def connect(address: String) {connect(List(address))}
    def close(): Unit
    def listen(listener: JournalListener): Unit
  }

  trait JournalListener {
    def onJournalCommit(entry: JournalEntry): Unit
    def onJournalBlock(ref: Reference, index: BigInt): Unit
  }

  sealed abstract class JournalError extends Serializable

  case class JournalCommitError(what: String) extends JournalError {
    override def toString = "Journal Commit Error: " + what
  }
  
  case class JournalDuplicateError(ref: Reference) extends JournalError {
    override def toString = "Duplicate Journal Insert: " + ref
  }
  
  case class JournalReferenceError(ref: Reference) extends JournalError {
    override def toString = "Bad reference: " + ref
  }
}
