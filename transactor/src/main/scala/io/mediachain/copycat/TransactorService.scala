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
import io.mediachain.protocol.transactor.Transactor.JournalEvent.Event
import io.mediachain.protocol.transactor.Transactor.JournalEvent.Event.JournalBlockPublished
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

class TransactorService(client: Client,
                        executor: ExecutorService,
                        datastore: Datastore)
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {
  private val logger = LoggerFactory.getLogger(classOf[TransactorService])
  
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
    throw new RuntimeException("XXX Implement me")
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

  def journalEntryToEvent(entry: JournalEntry): JournalEvent = {
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
    JournalEvent().withEvent(event)
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

