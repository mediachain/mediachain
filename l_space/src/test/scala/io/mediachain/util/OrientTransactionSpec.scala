package io.mediachain.util

import gremlin.scala._
import io.mediachain.Types._
import io.mediachain.{BaseSpec, ForEachGraph}
import io.mediachain.util.GremlinUtils._

object OrientTransactionSpec extends BaseSpec with ForEachGraph[Graph] {

  def is =
    s2"""
        - commits transaction if successful: $commitsSuccessful
        - rolls back transaction on failure: $rollsbackFailed
        - supports nested transactions: $supportsNested
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

  def rollsbackFailed = pending

  def supportsNested = pending
}
