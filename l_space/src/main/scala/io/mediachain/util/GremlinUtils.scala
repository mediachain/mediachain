package io.mediachain.util

import cats.data.Xor
import gremlin.scala._
import io.mediachain.core.GraphError
import io.mediachain.core.GraphError.TransactionFailed
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph

object GremlinUtils {
  def beginTx(graph: Graph): Unit = graph match {
    case orientGraph: OrientGraph =>
      orientGraph.getRawDatabase.begin()
    case _ => graph.tx.open()
  }

  def commitTx(graph: Graph): Unit = graph match {
    case orientGraph: OrientGraph =>
      orientGraph.getRawDatabase.commit()
    case _ => graph.tx.commit()
  }

  def rollbackTx(graph: Graph): Unit = graph match {
    case orientGraph: OrientGraph =>
      orientGraph.getRawDatabase.rollback()
    case _ => graph.tx.rollback()
  }

  def withTransaction[T](graph: Graph)(f: => T): Xor[GraphError, T] = {

    beginTx(graph)

    val result: Xor[GraphError, T] =
      Xor.catchNonFatal(f).leftMap(TransactionFailed)

    result match {
      case Xor.Left(_) => rollbackTx(graph)
      case _ => commitTx(graph)
    }

    result
  }


  def withTransactionXor[T](graph: Graph)(f: => Xor[GraphError, T])
  : Xor[GraphError, T] = {
    beginTx(graph)

    val result: Xor[GraphError, T] =
      Xor.catchNonFatal(f)
        .leftMap(TransactionFailed)
        .flatMap(res => res)

    result match {
      case Xor.Left(er) => rollbackTx(graph)
      case _ => commitTx(graph)
    }

    result
  }
}
