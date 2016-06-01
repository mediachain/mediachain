package io.mediachain.copycat

import io.grpc.{Status, StatusRuntimeException}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.Datastore.{Datastore, MultihashReference, Reference}
import io.mediachain.protocol.transactor.Transactor.TransactorServiceGrpc
import io.mediachain.protocol.transactor.Transactor

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.{Duration, SECONDS}

class TransactorService(client: Client,
                        datastore: Datastore,
                        timeout: Duration = Duration(120, SECONDS),
                        implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {

  override def fetchObjectChainHead(request: Transactor.MultihashReference):
  Future[Transactor.MultihashReference] = {
    val ref = MultiHash.fromHex(request.reference)
      .map(MultihashReference.apply)
      .getOrElse {
        throw new StatusRuntimeException(Status.INVALID_ARGUMENT)
      }

    client
      .lookup(ref)
      .map { (x: Option[Reference]) =>
        x.map { ref =>
          Transactor.MultihashReference(new String(ref.toCborBytes))
        }.getOrElse {
          throw new StatusRuntimeException(Status.NOT_FOUND)
        }
      }
  }
}

