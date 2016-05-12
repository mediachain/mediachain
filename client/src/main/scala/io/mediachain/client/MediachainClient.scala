package io.mediachain.client

import cats.data.{Xor, XorT}
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.{JournalError, JournalListener}
import io.mediachain.transactor.Copycat.{ClientState, ClientStateListener}

import scala.concurrent.{ExecutionContext, Future}
import io.mediachain.protocol.Datastore._

import scala.concurrent.Future
import scala.util.{Failure, Success}


sealed trait MediachainClientEvent
object MediachainClientEvent {
  case class CanonicalAdded(ref: Reference) extends MediachainClientEvent
  case class CanonicalUpdated(chainRef: Reference) extends MediachainClientEvent
}

trait ClientEventListener {
  def onClientEvent(event: MediachainClientEvent)
}


sealed trait ClientError
object ClientError {
  case class NetworkError(cause: Throwable) extends ClientError
  case class Journal(journalError: JournalError) extends ClientError
}


// wrapper around copycat client interface
// so far only the low-level interface is defined, and only References to
// objects in the datastore are returned.
// TODO: define a high-level interface that returns CanonicalRecords and
// ChainCells directly from the datastore
trait MediachainClient {

  /**
    * @return the set of References to all known CanonicalRecords
    */
  def allCanonicalReferences: Set[Reference]

  /**
    * @param ref a Reference to a CanonicalRecord
    * @return a Future that returns a Reference to the current head of the
    *         canonical's chain, or None if no chain exists
    */
  def chainForCanonical(ref: Reference): Future[Option[Reference]]


  /**
    * Add an event listener to be informed of new and updated
    * mediachain records.
    * @param listener an event listener
    */
  def addListener(listener: ClientEventListener): Unit


  /**
    * Add a new CanonicalRecord to the system
    * @param canonicalRecord a new Entity or Artefact to add to the system
    * @return an Xor-wrapped future that will complete with either a ClientError or a
    *         Reference to the new record
    */
  def addCanonical(canonicalRecord: CanonicalRecord): XorT[Future, ClientError, Reference]


  /**
    * Update an existing CanonicalRecord
    * @param canonicalReference a Reference to the canonical to update
    * @param chainCell a ChainCell containing the new metadata for the record.
    * @return an Xor-wrapped future that will complete with either a ClientError
    *         or a Reference to the new head of the record's chain
    */
  def updateCanonical(canonicalReference: Reference, chainCell: ChainCell)
  : XorT[Future, ClientError, Reference]
}



class MediachainCopycatClient(datastore: Datastore)
  (implicit executionContext: ExecutionContext = ExecutionContext.global)
  extends MediachainClient with JournalListener with ClientStateListener
{
  import io.mediachain.transactor.Copycat

  def allCanonicalReferences = canonicalRefs

  def chainForCanonical(ref: Reference): Future[Option[Reference]] = {
    // TODO: handle disconnected cluster state
    cluster.lookup(ref)
  }

  def addListener(listener: ClientEventListener): Unit = {
    listeners += listener
  }



  /**
    * Add a new CanonicalRecord to the system
    *
    * @param canonicalRecord a new Entity or Artefact to add to the system
    * @return an Xor-wrapped future that will complete with either a ClientError or a
    *         Reference to the new record
    */
  def addCanonical(canonicalRecord: CanonicalRecord)
  : XorT[Future, ClientError, Reference] = ???

  /**
    * Update an existing CanonicalRecord
    *
    * @param canonicalReference a Reference to the canonical to update
    * @param chainCell          a ChainCell containing the new metadata for the record.
    * @return an Xor-wrapped future that will complete with either a ClientError
    *         or a Reference to the new head of the record's chain
    */
  def updateCanonical(canonicalReference: Reference, chainCell: ChainCell): XorT[Future, ClientError, Reference] = ???


var listeners: Set[ClientEventListener] = Set()
  var canonicalRefs: Set[Reference] = Set()
  var clusterClientState: ClientState = ClientState.Disconnected

  val cluster = Copycat.Client.build()
  cluster.listen(this)
  cluster.addStateListener(this)

  def connect(address: String) = {
    cluster.connect(address)
  }

  def close() = cluster.close()

  def onStateChange(state: ClientState): Unit = {
    import ClientState._
    clusterClientState = state

    state match {
      case Connected => catchupJournal()
      case _ => ()
    }
  }

  def catchupJournal(): Unit = {
    cluster.currentBlock.map { block =>
      block.entries.foreach(handleJournalEntry)
      block.chain.foreach(backtrackJournal)
    }
  }

  def backtrackJournal(blockRef: Reference): Unit = {
    val block = datastore.getAs[JournalBlock](blockRef)
      .getOrElse(
        // TODO: better failure handling
        throw new RuntimeException(s"Unable to fetch block with ref: $blockRef")
      )
    handleNewBlock(block)
  }

  private var latestBlockIndex: BigInt = -1
  private def handleNewBlock(block: JournalBlock): Unit = {
    block.entries.foreach(handleJournalEntry)

    if (block.index > latestBlockIndex) {
      block.chain.foreach(backtrackJournal)
      latestBlockIndex = block.index
    }
  }

  private def handleJournalEntry(entry: JournalEntry): Unit = {
    canonicalRefs += entry.ref
  }

  override def onJournalCommit(entry: JournalEntry): Unit =
    handleJournalEntry(entry)

  override def onJournalBlock(ref: Reference): Unit = {
    datastore.getAs[JournalBlock](ref).foreach(handleNewBlock)
  }
}


