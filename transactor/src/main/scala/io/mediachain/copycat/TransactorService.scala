package io.mediachain.copycat

import io.grpc
import io.grpc.{Status, StatusRuntimeException}
import io.mediachain.copycat.StateMachine.JournalStateMachine
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.Datastore.{Datastore, MultihashReference, Reference}
import io.mediachain.protocol.transactor.Transactor.TransactorServiceGrpc
import io.mediachain.protocol.transactor.Transactor

import scala.concurrent.{Future, ExecutionContext}

class TransactorService(stateMachine: JournalStateMachine,
                        datastore: Datastore,
                        implicit val executionContext: ExecutionContext)
  extends TransactorServiceGrpc.TransactorService {

  override def fetchObjectChainHead(request: Transactor.MultihashReference):
  Future[Transactor.MultihashReference] = Future {
    val ref = MultiHash.fromHex(request.reference)
      .map(MultihashReference.apply)
      .getOrElse {
        throw new StatusRuntimeException(Status.INVALID_ARGUMENT)
      }

    stateMachine.lookup(ref)
      .map { x =>
        val str = new String(x.toCborBytes)
        Transactor.MultihashReference(str)
      }
      .getOrElse {
        throw new StatusRuntimeException(Status.NOT_FOUND)
      }
  }
}

