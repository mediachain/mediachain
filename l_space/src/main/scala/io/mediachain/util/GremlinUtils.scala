package io.mediachain.util

import cats.data.Xor
import gremlin.scala._
import io.mediachain.GraphError
import io.mediachain.GraphError.TransactionFailed

object GremlinUtils {

  def withTransaction[T](graph: Graph)(f: => T): Xor[GraphError, T] = {
    val isNested = graph.tx.isOpen
    if (!isNested) graph.tx.open()

    val result: Xor[GraphError, T] =
      Xor.catchNonFatal(f).leftMap(TransactionFailed)

    if (!isNested) {
      result match {
        case Xor.Left(_) => graph.tx.rollback()
        case _ => graph.tx.commit()
      }
    }

    result
  }


  def withTransactionXor[T](graph: Graph)(f: => Xor[GraphError, T])
  : Xor[GraphError, T] = {
    val isNested = graph.tx.isOpen
    if (!isNested) graph.tx.open()

    val result: Xor[GraphError, T] =
      Xor.catchNonFatal(f)
        .leftMap(TransactionFailed)
        .flatMap(res => res)

    if (!isNested) {
      result match {
        case Xor.Left(er) => graph.tx.rollback()
        case _ => graph.tx.commit()
      }
    }

    result
  }
}
