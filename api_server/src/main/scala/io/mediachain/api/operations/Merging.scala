package io.mediachain.api.operations

import java.util.UUID

import gremlin.scala._
import io.mediachain.Types._
import io.mediachain.Merge
import io.mediachain.Traversals
import Traversals._
import cats.data.Xor
import io.mediachain.core.GraphError


object Merging {

  def mergeCanonicals(childCanonicalID: UUID, parentCanonicalID: UUID)
    (graph: Graph): Xor[GraphError, Canonical] =
    for {
      parent <-
      canonicalsWithUUID(graph.V, parentCanonicalID).findCanonicalXor

      child <-
      canonicalsWithUUID(graph.V, childCanonicalID).findCanonicalXor

      mergeResult <- Merge.mergeCanonicals(graph, child, parent)
    } yield mergeResult


}
