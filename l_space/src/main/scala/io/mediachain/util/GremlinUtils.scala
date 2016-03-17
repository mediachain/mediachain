package io.mediachain.util

import cats.data.Xor
import gremlin.scala._
import io.mediachain.GraphError
import io.mediachain.GraphError.TransactionFailed

object GremlinUtils {

  def withTransaction[T](graph: Graph)(f: => T): Xor[GraphError, T] = {
    if (!graph.tx.isOpen) {
      graph.tx.open()
    }
    Xor.catchNonFatal {
      f
    }.leftMap(ex => {
      graph.tx.rollback()
      TransactionFailed(ex)
    })
      .map(result => {
        graph.tx.commit()
        result
      })
  }

}
