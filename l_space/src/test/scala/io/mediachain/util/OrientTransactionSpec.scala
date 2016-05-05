package io.mediachain.util

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Types._
import io.mediachain.core.GraphError
import io.mediachain.core.GraphError.CanonicalNotFound
import io.mediachain.{BaseSpec, ForEachGraph}
import io.mediachain.util.GremlinUtils._

object OrientTransactionSpec extends BaseSpec with ForEachGraph[Graph] {

  def is = sequential ^
    s2"""
        - commits transaction if successful $commitsSuccessful
        - rolls back transaction on failure $rollsBackFailed
        - rolls back on Xor.Left $rollsBackXorLeft
        - supports nested transactions $supportsNested
      """

  // just pass the graph through to each test
  def forEachGraph(graph: Graph) = graph

  def commitsSuccessful = { graph: Graph =>
    val result = withTransaction(graph) {
      graph + Canonical.create()
    }

    result must beRightXor { vertex: Vertex =>
      vertex.id must not beNull
    }
  }

  def rollsBackFailed = { graph: Graph =>
    val result = withTransaction(graph) {
      graph + Canonical.create()
      throw new Exception("The server is on fire! Abort! Abort!")
    }

    result must beLeftXor
    graph.V.hasLabel[Canonical].toList must beEmpty
  }

  def rollsBackXorLeft = { graph: Graph =>
    val result: Xor[GraphError, Unit] =
      withTransactionXor(graph) {
        graph + Canonical.create()
        Xor.left(CanonicalNotFound())
      }

    result must beLeftXor
    graph.V.hasLabel[Canonical].toList must beEmpty
  }

  def supportsNested = { graph: Graph =>
    val result: Xor[GraphError, Vertex] =
      withTransaction(graph) {
        val canonicalV = graph + Canonical.create()
        withTransaction(graph) {
          throw new Exception("Error in nested tx")
        }

        graph + Person(None, "foo")
      }

    result must beLeftXor

    graph.V.hasLabel[Canonical].toList must beEmpty
    graph.V.hasLabel[Person].toList must beEmpty
  }
}
