package io.mediachain

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Types._

object Merge {

  def mergeCanonicals(graph: Graph, child: Canonical, parent: Canonical)
  : Xor[GraphError, Canonical] = {

    for {
      childV <- child.vertex(graph)
      parentV <- parent.vertex(graph)
      childRootBlobEdges = childV.outE(DescribedBy).toList
      childRootBlobVs = childRootBlobEdges.map(_.inVertex)
    } yield {
      childRootBlobEdges.foreach(_.setProperty(Keys.Deprecated, true))
      childRootBlobVs.foreach(v => parentV --- DescribedBy --> v)
      childV --- SupersededBy --> parentV
      parent
    }
  }

}
