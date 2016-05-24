package io.mediachain.copycat

import io.grpc.stub.StreamObserver
import io.mediachain.protocol.transactor.Transactor._

class TransactorService extends TransactorServiceGrpc.TransactorService {
  override def fetchObjectChainHead(
    request: FetchObjectChainRequest,
    responseObserver: StreamObserver[ObjectCell]): Unit = {
    ???
  }
}

