package io.mediachain.api.operations

import java.util.UUID

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Traversals
import io.mediachain.Types._
import io.mediachain.core.GraphError.SubtreeError
import org.json4s._
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.Extraction

object CanonicalQueries {
  import Traversals.GremlinScalaImplicits
  import org.json4s.JsonDSL._
  implicit val formats = DefaultFormats

  val PAGE_SIZE = 20

  def listCanonicals(page: Int)(graph: Graph): List[Canonical] = {
    val first = page * PAGE_SIZE
    val last = first + PAGE_SIZE

    graph.V.hasLabel[Canonical].toCC[Canonical]
      .range(first, last).toList
  }

  def canonicalWithID(canonicalID: UUID)(graph: Graph): Option[Canonical] =
    Traversals.canonicalsWithUUID(graph.V, canonicalID)
      .toCC[Canonical]
      .headOption

  def historyForCanonical(canonicalID: UUID)(graph: Graph): Option[JObject] = {
    val treeXor = Traversals.canonicalsWithUUID(graph.V, canonicalID).findSubtreeXor

    for {
      tree <- treeXor.toOption
      canonicalGS = Traversals.canonicalsWithUUID(tree.V, canonicalID)
      canonical <- canonicalGS.toCC[Canonical].headOption
      revisions = Traversals.describingOrModifyingBlobs(tree.V, canonical).toCC[ImageBlob].toList
    } yield {
      val revisionsJ = revisions.map(Extraction.decompose)
      ("canonicalID" -> canonical.canonicalID) ~
        ("revisions" -> revisionsJ)
    }
  }
}
