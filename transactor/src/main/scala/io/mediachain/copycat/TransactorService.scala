package io.mediachain.copycat

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

import cats.data.Xor
import dogs.Streaming
import io.grpc.stub.StreamObserver
import io.grpc.{ServerBuilder, Status, StatusRuntimeException}
import io.mediachain.copycat.Client.{ClientState, ClientStateListener}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.CborSerialization
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.{JournalError, JournalListener}
import io.mediachain.protocol.transactor.Transactor.{MultihashReference => _, _}
import io.mediachain.protocol.transactor.Transactor
import io.mediachain.protocol.transactor.Transactor.JournalEvent.Event.JournalBlockPublished
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.collection.mutable.{Queue => MQueue, Stack => MStack}

class BackfillRunner(offset: Reference,
                     head: Reference,
                     observer: StreamObserver[JournalEvent],
                     datastore: Datastore)
  extends Runnable {
  private val logger = LoggerFactory.getLogger(classOf[BackfillRunner])
  private val backfillStack: MStack[JournalEvent] = MStack()
  private val queue: MQueue[JournalEvent] = MQueue()
  private var backfilled: AtomicBoolean = new AtomicBoolean(false)
  @volatile private var queueEmptied = false

  /** For the time being, this naively executes in a single thread.
    * This should eventually become a batched read of a few chain blocks
    * followed by re-enqueuing this worker in the executor with an
    * updated offset.
    */
  override def run(): Unit = {
    import TransactorService.refToRPCMultihashRef

    val chain = datastore.get(head).collect {
      case x: JournalBlock => x.toStream(datastore)
    }.getOrElse(Streaming.empty).map(_.chain)

    logger.debug(s"backfiller started. stream: $chain")

    /*
    a note: i'd really prefer to do something like

    stream.flatMap(_.map(Streaming.apply).getOrElse(Streaming.empty))

    but i'm not sure if that breaks the original continuation i'd set
    up. i'll investigate. @bigs
     */
    Streaming.cons(Some(head), chain)
      .takeWhile(x => x.isDefined && !x.contains(offset))
      .iterator
      .flatten
      .foreach { x =>
        val event = JournalEvent(
          JournalBlockPublished(refToRPCMultihashRef(x))
        )
        logger.debug(s"building backfilled event: $event")
        backfillStack.push(event)
      }

    logger.debug("filled backfill stack")

    backfillStack.foreach(e => {
        logger.debug(s"sending backfilled event: $e")
        observer.onNext(e)
      }
    )

    logger.debug("backfill complete")

    backfilled.set(true)
    while (queue.nonEmpty) {
      observer.onNext(queue.dequeue())
    }
    logger.debug("event queue empty")
    queueEmptied = true
  }

  def onNext(event: JournalEvent): Unit = {
    if (backfilled.get) {
      while (!queueEmptied) {
        Thread.`yield`()
      }

      observer.onNext(event)
    } else {
      queue.enqueue(event)
    }
  }
}

class TransactorListener(executor: ExecutorService,
                         datastore: Datastore,
                         client: Client)
  extends ClientStateListener with JournalListener {

  import collection.mutable.{Set => MSet, Map => MMap}
  import TransactorService.refToRPCMultihashRef

  type OnNextFn = JournalEvent => Unit
  type ObserverMap = MMap[StreamObserver[JournalEvent], OnNextFn]

  private val logger = LoggerFactory.getLogger(classOf[TransactorService])
  private val observers: ObserverMap = MMap()

  private def makeContinuation(offset: MultihashReference,
                               streamObserver: StreamObserver[JournalEvent])
  : OnNextFn = {
    import scala.concurrent.ExecutionContext.Implicits.global

    Await.result(client.currentBlock, Duration(30, SECONDS)) match {
      case JournalBlock(_, Some(chain), _) =>
        val backfiller =
          new BackfillRunner(offset, chain, streamObserver, datastore)
        executor.execute(backfiller)

        backfiller.onNext
      case _ =>
        // for now, just subscribe them. we can decide on error handling
        // logic later.
        streamObserver.onNext
    }

  }

  def addObserver(streamObserver: StreamObserver[JournalEvent],
                  offset: Option[MultihashReference]): Unit = {
    observers.synchronized {
      val onNext: OnNextFn = offset match {
        case Some(o) => makeContinuation(o, streamObserver)
        case _ => streamObserver.onNext
      }

      observers += (streamObserver -> onNext)
    }
  }

  override def onStateChange(state: ClientState): Unit = {
    logger.info(s"copycat state changed to $state")
    if (state == ClientState.Disconnected) {
      observers.synchronized {
        observers.foreach { case (observer, _) =>
          observer.onError(
            new StatusRuntimeException(
              Status.UNAVAILABLE
                .withDescription("disconnected from transactor cluster")
            )
          )
        }
        observers.clear()
      }
    }
  }

  override def onJournalCommit(entry: JournalEntry): Unit = {
    import JournalEvent.Event

    val event = entry match {
      case CanonicalEntry(_, ref) =>
        Event.CanonicalInserted(refToRPCMultihashRef(ref))

      case ChainEntry(_, ref, chain, chainPrevious) =>
        Event.ChainUpdated(
          UpdateChainResult(chainPrevious = chainPrevious.map(refToRPCMultihashRef))
            .withCanonical(refToRPCMultihashRef(ref))
            .withChain(refToRPCMultihashRef(chain))
        )
    }
    publishEvent(event)
  }

  override def onJournalBlock(ref: Reference): Unit = {
    import JournalEvent.Event
    val event = Event.JournalBlockPublished(
      refToRPCMultihashRef(ref)
    )

    publishEvent(event)
  }


  private def publishEvent(event: JournalEvent.Event): Unit = {
    observers.synchronized {
      val cancelledObservers: MSet[StreamObserver[JournalEvent]] = MSet()
      observers.foreach { case (observer, onNext) =>
        try {
          onNext(JournalEvent().withEvent(event))
        } catch {
          case e: StatusRuntimeException if e.getStatus == Status.CANCELLED =>
            // if the client killed the connection, remove the observer
            cancelledObservers.add(observer)
          case t: Throwable =>
            throw t
        }
      }

      observers --= cancelledObservers
    }
  }
}

class TransactorService(client: Client,
                        executor: ExecutorService,
                        datastore: Datastore,
                        timeout: Duration = Duration(120, SECONDS))
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {
  private val logger = LoggerFactory.getLogger(classOf[TransactorService])
  private val listener = new TransactorListener(executor, datastore, client)

  client.addStateListener(listener)
  client.listen(listener)

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
    responseObserver: StreamObserver[JournalEvent]): Unit = {

    val offset = for {
      rpcRef <- request.lastJournalBlock
      multihash = MultiHash.fromBase58(rpcRef.reference).getOrElse(
        throw new StatusRuntimeException(
          Status.INVALID_ARGUMENT.withDescription("Multihash reference is invalid")
        ))
    } yield MultihashReference(multihash)

    listener.addObserver(responseObserver, offset)
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


  def createServerThread(service: TransactorService,
                         executor: ExecutorService,
                         port: Int)
                        (implicit executionContext: ExecutionContext)
  : Unit = {
    import scala.language.existentials

    val builder = ServerBuilder.forPort(port)
    val server = builder.addService(
      TransactorServiceGrpc.bindService(service, executionContext)
    ).build

    executor.submit(new Runnable {
      def run {
        server.start
      }})
  }
}

