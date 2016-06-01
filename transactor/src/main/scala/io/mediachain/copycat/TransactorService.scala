package io.mediachain.copycat

import java.util.concurrent.ExecutorService

import cats.data.Xor
import io.grpc.{ServerBuilder, Status, StatusRuntimeException}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.CborSerialization
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.JournalError
import io.mediachain.protocol.transactor.Transactor.{InsertRequest, TransactorServiceGrpc, UpdateRequest}
import io.mediachain.protocol.transactor.Transactor

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, SECONDS}

class TransactorService(client: Client,
                        timeout: Duration = Duration(120, SECONDS))
                       (implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {

  override def fetchObjectChainHead(request: Transactor.MultihashReference):
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

  override def insertCanonicalRecord(request: InsertRequest)
  : Future[Transactor.MultihashReference] = {
    val recXor = CborSerialization.fromCborBytes[CanonicalRecord](
      request.canonicalCbor.toByteArray
    )

    val future = recXor match {
      case Xor.Left(err) => throw new StatusRuntimeException(
        Status.FAILED_PRECONDITION.withDescription(
          s"Object deserialization error: ${err.message}"
        )
      )
      case Xor.Right(obj) =>
        client.insert(obj)
    }

    future.map { entryXor: Xor[JournalError, CanonicalEntry] =>
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

  override def updateCanonicalRecord(request: UpdateRequest)
  : Future[Transactor.MultihashReference] = {
    val cellXor = CborSerialization.fromCborBytes[ChainCell](
      request.chainCellCbor.toByteArray
    )

    val future = cellXor match {
      case Xor.Left(err) => throw new StatusRuntimeException(
        Status.FAILED_PRECONDITION.withDescription(
          s"Object deserialization error: ${err.message}"
        )
      )
      case Xor.Right(cell) =>
        client.update(cell.ref, cell)
    }

    future.map { entryXor: Xor[JournalError, ChainEntry] =>
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
      override def run(): Unit = {
        server.start
      }
    })
  }
}

