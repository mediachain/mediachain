package io.mediachain.copycat

import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue}

import cats.data.Xor
import com.amazonaws.AmazonClientException
import io.grpc.stub.StreamObserver
import io.grpc._
import io.mediachain.copycat.Client.{ClientState, ClientStateListener}
import io.mediachain.util.{Metrics, Grpc}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.CborSerialization
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.{JournalError, JournalDuplicateError, JournalListener}
import io.mediachain.protocol.transactor.Transactor
import io.mediachain.protocol.transactor.Transactor._
import io.mediachain.protocol.types.Types
import io.mediachain.protocol.types.Types.{ChainReference, NullReference}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.util.{Failure, Success, Try}

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
  
  override def onStateChange(cstate: ClientState) {
    cstate match {
      case ClientState.Suspended =>
        exec.submit(new Runnable {
            def run {
              logger.info("Copycat connection suspened; suspending streaming")
              state.streaming = false
            }})
        
      case ClientState.Connected =>
        exec.submit(new Runnable {
          def run {
            logger.info("Copycat connection recovered; synchronizing current block")
            withErrorLog(fetchCurrentBlock())
          }})
        
      case ClientState.Disconnected =>
        exec.submit(new Runnable {
          def run {
            logger.info("Copycat connection severed; disconnecting observers")
            withErrorLog(disconnect())
          }})
    }
  }
  
  override def onJournalCommit(entry: JournalEntry) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(journalCommitEvent(entry))
      }})
  }
  
  override def onJournalBlock(ref: Reference, index: BigInt) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(journalBlockEvent(ref, index))
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
    dispatching -= observer
  }
  
  private def journalCommitEvent(entry: JournalEntry) {
    if (state.streaming && entry.index >= state.index) {
      if (entry.index != state.index) {
        logger.warn(s"Commit skipped entries; expected ${state.index} but got ${entry.index}")
      }
      state.block += entry
      state.index = entry.index + 1
      emitEvent(TransactorService.journalEntryToEvent(entry)) 
    }
  }
  
  private def journalBlockEvent(ref: Reference, index: BigInt) {
    if (state.streaming && index >= state.index) {
      if (index != state.index) {
        logger.warn(s"Block skipped entries; expected ${state.index} but got ${index}")
      }
      state.blockchain = Some(ref)
      state.block.clear()
      state.index = index
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

  private def disconnect() {
    state.streaming = false
    observers.keys.foreach { observer =>
      Try(observer.onError(
        new StatusRuntimeException(
          Status.UNAVAILABLE.withDescription("Transactor network error")
        )))
    }
    observers = Map()
    dispatching = Set()
  }
  
  private def fetchCurrentBlock() {
    def setCurrentBlock(block: JournalBlock) {
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
    }
    
    def extendCurrentBlock(block: JournalBlock) {
      val newentries = block.entries.drop(state.block.length)
      state.index = block.index
      state.block ++= newentries
      newentries.foreach { entry =>
        emitEvent(TransactorService.journalEntryToEvent(entry))
      }
    }
    
    def fetchBlocks(chainHead: Option[Reference]) = {
      def loop(ref: Reference, blocks: List[JournalBlock])
      : List[JournalBlock]
      = {
        val block = getBlock(ref)
        logger.info(s"Fetched block ${ref} -> ${block.index} ${block.chain}")
        if (block.chain == state.blockchain) {
          block :: blocks
        } else if (block.chain.isEmpty || block.index < state.index) {
          throw new RuntimeException("PANIC: Blockchain divergence detected")
        } else {
          loop(block.chain.get, block :: blocks)
        }
      }
      
      chainHead match {
        case Some(ref) => loop(ref, Nil)
        case None => Nil
      }
    }
    
    def getBlock(ref: Reference) = {
      def loop(retry: Int): JournalBlock = {
        Try(datastore.getAs[JournalBlock](ref)) match {
          case Success(Some(block)) => block
          case Success(None) =>
            val backoff = Client.randomBackoff(retry)
            logger.info(s"Missing block ${ref}; retrying in ${backoff} ms")
            Thread.sleep(backoff)
            loop(retry + 1)
            
            // be tolerant of dynamo datastore transient failures
          case Failure(err: AmazonClientException) =>
            logger.error("AWS error", err)
            val backoff = Client.randomBackoff(retry)
            logger.info(s"Retrying ${ref} in ${backoff} ms")
            Thread.sleep(backoff)
            loop(retry + 1)
            
          case Failure(err: Throwable) =>
            throw err
        }
      }
      
      loop(0)
    }
    
    val block = Await.result(client.currentBlock, Duration.Inf)
    if (state.isEmpty) {
      setCurrentBlock(block)
    } else if (block.index < state.index) {
      logger.warn(s"Cluster replayed STALE BLOCK ${block.index}; current index is ${state.index}")
    } else if (block.chain == state.blockchain) {
      extendCurrentBlock(block)
    } else if (block.chain.isEmpty) {
      throw new RuntimeException("PANIC: Blockchain divergence detected")
    } else {
      logger.info(s"Resyncing blockchain ${state.index} ${state.blockchain} -> ${block.index} ${block.chain}")
      val xblocks = fetchBlocks(block.chain)
      extendCurrentBlock(xblocks.head)
      xblocks.tail.foreach(setCurrentBlock(_))
      setCurrentBlock(block)
    }
    
    state.streaming = true
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
        
      case e: Exception =>
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

class TransactorService(client: Client, datastore: Datastore, metrics: Option[Metrics])
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {
  private val logger = LoggerFactory.getLogger(classOf[TransactorService])  
  private val listener = new TransactorListener(client, datastore)
  listener.start()

  private def rpcMetrics(rpc: String) {
    metrics.foreach { m =>
      m.counter("transactor_rpc", Map("rpc" -> rpc))
    }
  }
  
  private def rpcErrorMetrics(rpc: String, what: String) {
    metrics.foreach { m =>
      m.counter("transactor_error", Map("rpc" -> rpc, "error" -> what))
    }
  }

  private def rpcRecordMetrics(entry: JournalEntry) {
    metrics.foreach { m =>
      val ctags = entry match {
        case e: CanonicalEntry => 
          Map("metric" -> "record", "record" -> "canonical")
        case e: ChainEntry =>
          Map("metric" -> "record", "record" -> "chain")
      }
      m.counter("transactor_object", ctags)
      m.gauge("transactor_object", Map("metric" -> "index"), entry.index.toLong)
    }
  }

  private def internalError[U](rpc: String)
  : PartialFunction[Throwable, Future[U]] = {
    case err: Throwable =>
      rpcErrorMetrics(rpc, "INTERNAL")
      Future.failed { 
        new StatusRuntimeException(
          Status.INTERNAL.withDescription(err.toString)
        )
      }
  }
  
  override def lookupChain(request: Types.MultihashReference):
  Future[Types.ChainReference] = {
    rpcMetrics("lookupChain")
    
    val ref = MultiHash.fromBase58(request.reference)
      .map(MultihashReference.apply)
      .getOrElse {
        rpcErrorMetrics("lookupChain", "INVALID_ARGUMENT")
        throw new StatusRuntimeException(
          Status.INVALID_ARGUMENT.withDescription(
            "Invalid multihash reference"
          ))
      }

    client.lookup(ref)
      .recoverWith(internalError("lookupChain"))
      .map { 
      case Xor.Right(ref) =>
        TransactorService.refOptToChainRef(ref)
      case Xor.Left(err) =>
        rpcErrorMetrics("lookupChain", "NOT_FOUND")
        throw new StatusRuntimeException(
          Status.NOT_FOUND.withDescription(err.toString)
        )
    }
  }

  override def insertCanonical(request: InsertRequest)
  : Future[Types.MultihashReference] = {
    rpcMetrics("insertCanonical")
    
    val bytes = request.canonicalCbor.toByteArray
    checkRecordSize(bytes, "insertCanonical")
    
    val recXor = CborSerialization.fromCborBytes[CanonicalRecord](bytes)

    val insertF = recXor match {
      case Xor.Left(err) => 
        rpcErrorMetrics("insertCanonical", "INVALID_ARGUMENT")
        throw new StatusRuntimeException(
          Status.INVALID_ARGUMENT.withDescription(
            s"Object deserialization error: ${err.message}"
          )
        )
      case Xor.Right(obj) =>
        client.insert(obj)
    }

    insertF.recoverWith(internalError("insertCanonical"))
      .map { entryXor: Xor[JournalError, CanonicalEntry] =>
      entryXor match {
        case Xor.Left(err) =>
          rpcErrorMetrics("insertCanonical", "FAILED_PRECONDITION")
          
          val meta = err match {
            case JournalDuplicateError(MultihashReference(multihash)) =>
              val meta = new Metadata
              meta.put(Metadata.Key.of("Reference", Metadata.ASCII_STRING_MARSHALLER),
                       multihash.base58)
              meta
            case _ => null
          }
          
          throw new StatusRuntimeException(
            Status.FAILED_PRECONDITION.withDescription(s"Journal Error: $err"),
            meta            
          )
          
        case Xor.Right(entry) =>
          rpcRecordMetrics(entry)
          TransactorService.refToRPCMultihashRef(entry.ref)
      }
    }
  }
  
  override def updateChain(request: UpdateRequest)
  : Future[Types.MultihashReference] = {
    rpcMetrics("updateChain")
    
    val bytes = request.chainCellCbor.toByteArray
    checkRecordSize(bytes, "updateChain")

    val cellXor = CborSerialization.fromCborBytes[ChainCell](bytes)

    val updateF = cellXor match {
      case Xor.Left(err) => 
        rpcErrorMetrics("updateChain", "INVALID_ARGUMENT")
        throw new StatusRuntimeException(
          Status.INVALID_ARGUMENT.withDescription(
            s"Object deserialization error: ${err.message}"
          )
        )
      case Xor.Right(cell) =>
        client.update(cell.ref, cell)
    }

    updateF.recoverWith(internalError("updateChain"))
      .map { entryXor: Xor[JournalError, ChainEntry] =>
      entryXor match {
        case Xor.Left(err) =>
          rpcErrorMetrics("updateChain", "FAILED_PRECONDITION")
          throw new StatusRuntimeException(
            Status.FAILED_PRECONDITION.withDescription(
              s"Journal Error: $err"
            )
          )
        case Xor.Right(entry) =>
          rpcRecordMetrics(entry)
          TransactorService.refToRPCMultihashRef(entry.chain)
      }
    }
  }

  override def journalStream(request: JournalStreamRequest,
                             observer: StreamObserver[JournalEvent]) {
    rpcMetrics("journalStream")
    listener.addObserver(observer)
  }


  val maxRecordSize = 64 * 1024 // 64k ought to be enough for everyone
  private def checkRecordSize(bytes: Array[Byte], rpc: String) {
    if (bytes.length > maxRecordSize) {
      rpcErrorMetrics(rpc, "INVALID_ARGUMENT")
      throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription("Maximum record size exceeded"))
    }
  }
}

object TransactorService {
  def refToRPCMultihashRef(ref: Reference)
  : Types.MultihashReference = ref match {
    case MultihashReference(multihash) =>
      Types.MultihashReference(multihash.base58)
    case r =>
      throw new ClassCastException(
        s"Expected MultihashReference, got type ${r.getClass.getTypeName}"
      )
  }
  
  def refOptToChainRef(opt: Option[Reference])
  : ChainReference = opt match {
    case Some(ref) =>
      ChainReference(ChainReference.Reference.Chain(refToRPCMultihashRef(ref)))
    case None =>
      ChainReference(ChainReference.Reference.Nil(NullReference()))
  }
  
  def journalBlockReferenceToEvent(ref: Reference): JournalEvent = {
    JournalEvent(JournalEvent.Event.JournalBlockEvent(refToRPCMultihashRef(ref)))
  }

  def journalEntryToEvent(entry: JournalEntry): JournalEvent = {
    val event = entry match {
      case CanonicalEntry(_, ref) =>
        JournalEvent.Event.InsertCanonicalEvent(refToRPCMultihashRef(ref))

      case ChainEntry(_, ref, chain, chainPrevious) =>
        JournalEvent.Event.UpdateChainEvent(
          UpdateChainResult(chainPrevious = chainPrevious.map(refToRPCMultihashRef))
            .withCanonical(refToRPCMultihashRef(ref))
            .withChain(refToRPCMultihashRef(chain))
        )
    }
    JournalEvent(event)
  }

  def createServer(service: TransactorService, port: Int)
                  (implicit executionContext: ExecutionContext)
  = {
    import scala.language.existentials
    
    val builder = ServerBuilder.forPort(port)
    val server = builder.addService(
      ServerInterceptors.intercept(
        TransactorServiceGrpc.bindService(service, executionContext),
        Grpc.loggingInterceptor("TransactorService")
      )
    ).build
    server
  }
}

