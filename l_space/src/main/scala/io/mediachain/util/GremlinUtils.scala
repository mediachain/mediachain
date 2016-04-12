package io.mediachain.util

import cats.data.Xor
import com.orientechnologies.orient.core.tx.ORollbackException
import gremlin.scala._
import io.mediachain.core.GraphError
import io.mediachain.core.GraphError.TransactionFailed
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph

object GremlinUtils {
  def beginTx(graph: Graph): Xor[TransactionFailed, Unit] = graph match {
    case orientGraph: OrientGraph =>
      Xor.catchNonFatal {
        orientGraph.getRawDatabase.begin()
        ()
      }.leftMap(TransactionFailed)
    case _ => Xor.catchNonFatal(graph.tx.open())
      .leftMap(TransactionFailed)
  }

  def commitTx(graph: Graph): Xor[TransactionFailed, Unit] = graph match {
    case orientGraph: OrientGraph =>
      Xor.catchNonFatal{
        orientGraph.getRawDatabase.commit()
        ()
      }.leftMap(TransactionFailed)
    case _ =>
      Xor.catchNonFatal(graph.tx.commit())
        .leftMap(TransactionFailed)
  }

  def rollbackTx(graph: Graph): Xor[TransactionFailed, Unit] = graph match {
    case orientGraph: OrientGraph =>
      Xor.catchNonFatal {
        orientGraph.getRawDatabase.rollback()
        ()
      }.leftMap(TransactionFailed)
    case _ => Xor.catchNonFatal(graph.tx.rollback())
      .leftMap(TransactionFailed)
  }

  def withTransaction[T](graph: Graph)(f: => T): Xor[GraphError, T] =
    for {
      _ <- beginTx(graph)

      resultXor = Xor.catchNonFatal(f).leftMap(TransactionFailed)

      _ <- resultXor match {
        case Xor.Left(_) => rollbackTx(graph)
        case _ => commitTx(graph)
      }

      result <- resultXor
    }  yield result


  def withTransactionXor[T](graph: Graph)(f: => Xor[GraphError, T])
  : Xor[GraphError, T] =
    for {
      _ <- beginTx(graph)

      resultXor = Xor.catchNonFatal(f)
        .leftMap(TransactionFailed)
        .flatMap(res => res)

      _ <- resultXor match {
        case Xor.Left(_) => rollbackTx(graph)
        case _ => commitTx(graph)
      }

      result <- resultXor
    } yield result
}
