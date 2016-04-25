package io.mediachain.rpc.operations

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Merge
import io.mediachain.Traversals, Traversals._, Traversals.Implicits._
import io.mediachain.core.GraphError
import io.mediachain.rpc.Services.MergeCanonicalsResponse

object Merging {
  def mergeCanonicals(childCanonicalID: String, parentCanonicalID: String)
    (graph: Graph): Xor[GraphError, MergeCanonicalsResponse] =
      for {
        parent <- graph.V ~>
        canonicalsWithID(parentCanonicalID) >> findCanonicalXor

        child <- graph.V ~>
        canonicalsWithID(childCanonicalID) >> findCanonicalXor

        mergeResult <- Merge.mergeCanonicals(graph, child, parent)

      } yield MergeCanonicalsResponse(
        mergedCanonicalID = mergeResult.canonicalID
      )

}
