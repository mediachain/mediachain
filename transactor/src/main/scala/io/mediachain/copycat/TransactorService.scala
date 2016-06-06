package io.mediachain.copycat

import java.util.concurrent.ExecutorService

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
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, SECONDS}

class TransactorService(client: Client,
                        timeout: Duration = Duration(120, SECONDS))
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService
  with ClientStateListener
  with JournalListener {
  private val logger = LoggerFactory.getLogger(classOf[TransactorService])

  client.addStateListener(this)
  client.listen(this)

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
          refToRPCMultihashRef(ref)
        }.getOrElse {
          throw new StatusRuntimeException(Status.NOT_FOUND)
        }
      }
  }

  private def refToRPCMultihashRef(ref: Reference)
  : Transactor.MultihashReference = ref match {
    case MultihashReference(multihash) =>
      Transactor.MultihashReference(multihash.base58)
    case r =>
      throw new ClassCastException(
        s"Expected MultihashReference, got type ${r.getClass.getTypeName}"
      )
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
          refToRPCMultihashRef(entry.ref)
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
          refToRPCMultihashRef(entry.chain)
      }
    }
  }


  import collection.mutable.{Set => MSet}
  private val journalEventObservers: MSet[StreamObserver[JournalEvent]] = MSet()

  override def journalStream(request: JournalStreamRequest,
    responseObserver: StreamObserver[JournalEvent]): Unit = {

    // TODO: play event stream following lastBlockRef in request.
    // this involves accessing the datastore, keeping local state
    // per-observer, etc.

    journalEventObservers.synchronized {
      journalEventObservers.add(responseObserver)
    }
  }

  val maxRecordSize = 64 * 1024 // 64k ought to be enough for everyone
  private def checkRecordSize(bytes: Array[Byte]) {
    if (bytes.length > maxRecordSize) {
      throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription("Maximum record size exceeded"))
    }
  }


  override def onStateChange(state: ClientState): Unit = {
    logger.info(s"copycat state changed to $state")
    if (state == ClientState.Disconnected) {
      journalEventObservers.synchronized {
        journalEventObservers.foreach { observer =>
          observer.onError(
            new StatusRuntimeException(
              Status.UNAVAILABLE
                .withDescription("disconnected from transactor cluster")
            )
          )
        }
        journalEventObservers.clear()
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
    journalEventObservers.synchronized {
      val cancelledObservers: MSet[StreamObserver[JournalEvent]] = MSet()
      journalEventObservers.foreach { observer =>
        try {
          observer.onNext(JournalEvent().withEvent(event))
        } catch {
          case e: StatusRuntimeException if e.getStatus == Status.CANCELLED =>
            // if the client killed the connection, remove the observer
            cancelledObservers.add(observer)
          case t: Throwable =>
            throw t
        }
      }

      journalEventObservers --= cancelledObservers
    }
  }

}

object TransactorService {
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

