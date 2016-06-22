package io.mediachain.copycat

import java.util.concurrent.{Executors, BlockingQueue, LinkedBlockingQueue}

import cats.data.Xor
import io.grpc.stub.StreamObserver
import io.grpc.{ServerBuilder, Status, StatusRuntimeException}
import io.mediachain.copycat.Client.{ClientState, ClientStateListener}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.CborSerialization
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.{JournalError, JournalListener}
import io.mediachain.protocol.transactor.Transactor.{MultihashReference => _, _}
import io.mediachain.protocol.transactor.Transactor
import io.mediachain.protocol.transactor.Transactor.JournalEvent.Event
import org.slf4j.LoggerFactory
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.util.{Try, Success, Failure}

class TransactorListenerState {
  var index: BigInt = -1
  var block: Buffer[JournalEntry] = new ArrayBuffer
  var blockchain: Option[Reference] = None
  var streaming: Boolean = false
  
  def isEmpty = (index == -1)
}

class TransactorListener(client: Client, datastore: Datastore)
extends ClientStateListener with JournalListener {
  type Observer = StreamObserver[JournalEvent]
  private val logger = LoggerFactory.getLogger(classOf[TransactorListener])
  // state: a view of the current block
  private val state = new TransactorListenerState
  // execution contexts
  //  exec: single threaded state manipulation
  //  dispatch: multi threaded event dispatch
  private val exec = Executors.newSingleThreadExecutor()
  private val dispatch = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime.availableProcessors))
  // observers: a Map of observer to a queue with pending events for in-order dispatch
  private var observers: Map[Observer, BlockingQueue[JournalEvent]] = Map()
  // dispatching: set of currently dispatching observers to resolve races
  private var dispatching: Set[Observer] = Set()

  def start() {
    client.listen(this)
    client.addStateListener(this)
    exec.submit(new Runnable {
      def run {
        withErrorLog(fetchCurrentBlock())
      }})
  }
  
  def addObserver(observer: Observer) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(addStreamObserver(observer))
      }})
  }
  
  def removeObserver(observer: Observer) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(removeStreamObserver(observer))
      }})
  }
  
  override def onStateChange(state: ClientState) {
    // XXX Implement me
  }
  
  override def onJournalCommit(entry: JournalEntry) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(journalCommitEvent(entry))
      }})
  }
  
  override def onJournalBlock(ref: Reference) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(journalBlockEvent(ref))
      }})
  }
  
  // serialized execution
  private def addStreamObserver(observer: Observer) {
    logger.info(s"Adding stream observer ${observer}")
    val queue = new LinkedBlockingQueue[JournalEvent]
    state.blockchain.foreach { ref => 
      queue.add(TransactorService.journalBlockReferenceToEvent(ref))
    }
    state.block.foreach { entry =>
      queue.add(TransactorService.journalEntryToEvent(entry))
    }
    observers += (observer -> queue)
    scheduleDispatch(observer)
  }
  
  private def removeStreamObserver(observer: Observer) {
    logger.info(s"Removing stream observer ${observer}")
    observers -= observer
  }
  
  private def journalCommitEvent(entry: JournalEntry) {
    if (state.streaming) {
      state.block += entry
      state.index = entry.index
      emitEvent(TransactorService.journalEntryToEvent(entry)) 
    }
  }
  
  private def journalBlockEvent(ref: Reference) {
    if (state.streaming) {
      state.blockchain = Some(ref)
      state.block.clear()
      emitEvent(TransactorService.journalBlockReferenceToEvent(ref))
    }
  }
  
  private def emitEvent(evt: JournalEvent) {
    observers.foreach {
      case (observer, queue) =>
        queue.add(evt)
        scheduleDispatch(observer)
    }
  }
  
  private def scheduleDispatch(observer: Observer) {
    if (!dispatching.contains(observer)) {
      dispatching += observer
      dispatch.submit(new Runnable {
        def run {
          withErrorLog(dispatchEvents(observer))
        }})
    }
  }
  
  private def rescheduleDispatch(observer: Observer) {
    observers.get(observer) match {
      case Some(queue) =>
        if (queue.isEmpty) {
          dispatching -= observer
        } else {
          dispatch.submit(new Runnable {
            def run {
              withErrorLog(dispatchEvents(observer))
            }})
        } 
        
      case None =>
        dispatching -= observer
    }
  }
  
  private def fetchCurrentBlock() {
    val block = Await.result(client.currentBlock, Duration.Inf)
    if (state.isEmpty) {
      state.index = block.index
      state.block.clear()
      state.block ++= block.entries
      state.blockchain = block.chain
      state.blockchain.foreach { ref =>
        emitEvent(TransactorService.journalBlockReferenceToEvent(ref))
      }
      state.block.foreach { entry =>
        emitEvent(TransactorService.journalEntryToEvent(entry))
      }
      state.streaming = true
    } else {
      // when recovering, we need to replay potentially missed entries
      // XXX Implement me
    }
  }
  
  // parallel dispatch
  private def dispatchEvents(observer: Observer) {
    def loop(queue: BlockingQueue[JournalEvent]) {
      val next = queue.poll()
      if (next != null) {
        observer.onNext(next)
        loop(queue)
      }
    }
    
    try {
      observers.get(observer).foreach(loop(_))
      exec.submit(new Runnable {
        def run {
          withErrorLog(rescheduleDispatch(observer))
        }})
    } catch {
      case e: StatusRuntimeException if e.getStatus == Status.CANCELLED =>
        logger.info(s"Observer ${observer} cancelled; scheduling removal")
        removeObserver(observer)
        
      case e: Throwable =>
        logger.error(s"Error dispatching event to ${observer}", e)
        Try(observer.onError(e))
        removeObserver(observer)
    }
  }

  // utils
  private def withErrorLog(expr: => Unit) {
    try {
      expr
    } catch {
      case e: Throwable =>
        logger.error("Unhandled exception in task", e)
    }
  }
}

class TransactorService(client: Client, datastore: Datastore)
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {
  private val logger = LoggerFactory.getLogger(classOf[TransactorService])  
  private val listener = new TransactorListener(client, datastore)
  listener.start()

  override def lookupChain(request: Transactor.MultihashReference):
  Future[Transactor.MultihashReference] = {
    val ref = MultiHash.fromBase58(request.reference)
      .map(MultihashReference.apply)
      .getOrElse {
        throw new StatusRuntimeException(Status.INVALID_ARGUMENT)
      }

    client
      .lookup(ref)
      .map { (x: Option[Reference]) =>
        x.map { ref =>
          TransactorService.refToRPCMultihashRef(ref)
        }.getOrElse {
          throw new StatusRuntimeException(Status.NOT_FOUND)
        }
      }
  }

  override def insertCanonical(request: InsertRequest)
  : Future[Transactor.MultihashReference] = {
    val bytes = request.canonicalCbor.toByteArray
    checkRecordSize(bytes)
    
    val recXor = CborSerialization.fromCborBytes[CanonicalRecord](bytes)

    val insertF = recXor match {
      case Xor.Left(err) => throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription(
          s"Object deserialization error: ${err.message}"
        )
      )
      case Xor.Right(obj) =>
        client.insert(obj)
    }

    insertF.map { entryXor: Xor[JournalError, CanonicalEntry] =>
      entryXor match {
        case Xor.Left(err) =>
          throw new StatusRuntimeException(
            Status.FAILED_PRECONDITION.withDescription(
              s"Journal Error: $err"
            )
          )
        case Xor.Right(entry) =>
          TransactorService.refToRPCMultihashRef(entry.ref)
      }
    }
  }
  
  override def updateChain(request: UpdateRequest)
  : Future[Transactor.MultihashReference] = {
    val bytes = request.chainCellCbor.toByteArray
    checkRecordSize(bytes)

    val cellXor = CborSerialization.fromCborBytes[ChainCell](bytes)

    val updateF = cellXor match {
      case Xor.Left(err) => throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription(
          s"Object deserialization error: ${err.message}"
        )
      )
      case Xor.Right(cell) =>
        client.update(cell.ref, cell)
    }

    updateF.map { entryXor: Xor[JournalError, ChainEntry] =>
      entryXor match {
        case Xor.Left(err) =>
          throw new StatusRuntimeException(
            Status.FAILED_PRECONDITION.withDescription(
              s"Journal Error: $err"
            )
          )
        case Xor.Right(entry) =>
          TransactorService.refToRPCMultihashRef(entry.chain)
      }
    }
  }

  override def journalStream(request: JournalStreamRequest,
                             observer: StreamObserver[JournalEvent]) {
    listener.addObserver(observer)
  }


  val maxRecordSize = 64 * 1024 // 64k ought to be enough for everyone
  private def checkRecordSize(bytes: Array[Byte]) {
    if (bytes.length > maxRecordSize) {
      throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription("Maximum record size exceeded"))
    }
  }
}

object TransactorService {
  def refToRPCMultihashRef(ref: Reference)
  : Transactor.MultihashReference = ref match {
    case MultihashReference(multihash) =>
      Transactor.MultihashReference(multihash.base58)
    case r =>
      throw new ClassCastException(
        s"Expected MultihashReference, got type ${r.getClass.getTypeName}"
      )
  }
  
  def journalBlockReferenceToEvent(ref: Reference): JournalEvent = {
    JournalEvent().withEvent(Event.JournalBlockEvent(refToRPCMultihashRef(ref)))
  }

  def journalEntryToEvent(entry: JournalEntry): JournalEvent = {
    val event = entry match {
      case CanonicalEntry(_, ref) =>
        Event.InsertCanonicalEvent(refToRPCMultihashRef(ref))

      case ChainEntry(_, ref, chain, chainPrevious) =>
        Event.UpdateChainEvent(
          UpdateChainResult(chainPrevious = chainPrevious.map(refToRPCMultihashRef))
            .withCanonical(refToRPCMultihashRef(ref))
            .withChain(refToRPCMultihashRef(chain))
        )
    }
    JournalEvent().withEvent(event)
  }


  def createServer(service: TransactorService, port: Int)
                  (implicit executionContext: ExecutionContext)
  = {
    import scala.language.existentials
    
    val builder = ServerBuilder.forPort(port)
    val server = builder.addService(
      TransactorServiceGrpc.bindService(service, executionContext)
    ).build
    server
  }
}

