package io.mediachain.api.operations

import java.util.UUID

import cats.data.Xor
import gremlin.scala._
import io.mediachain.{Query, Traversals}
import io.mediachain.Types._
import io.mediachain.core.GraphError.SubtreeError
import io.mediachain.util.JsonUtils
import org.json4s._
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.Extraction

object CanonicalQueries {
  import Traversals.GremlinScalaImplicits
  import org.json4s.JsonDSL._
  implicit val formats = DefaultFormats

  val PAGE_SIZE = 20

  def blobToJObject(metadataBlob: MetadataBlob): JObject =
    JsonUtils.jsonObjectForHashable(metadataBlob) ~
      ("multiHash" -> metadataBlob.multiHash.base58)


  def canonicalToBlobObject(graph: Graph, canonical: Canonical)
  : Option[JObject] = {
    // FIXME: this is a pretty sad n+1 query
    val gs = Traversals.canonicalsWithID(graph.V, canonical.canonicalID)
    val rootBlobOpt: Option[(String, MetadataBlob)] =
      gs.out(DescribedBy).headOption.flatMap { v: Vertex =>
        v.label match {
          case "ImageBlob" => Some((v.label, v.toCC[ImageBlob]))
          case "Person" => Some((v.label, v.toCC[Person]))
          case _ => None
        }
      }

    rootBlobOpt
      .map { labelWithBlob =>
        val (label: String, blob: MetadataBlob) = labelWithBlob
        val blobObject = blobToJObject(blob)

        ("canonicalID" -> canonical.canonicalID) ~
          ("artefact" -> (("type" -> label) ~ blobObject))
      }
  }

  def listCanonicals(page: Int)(graph: Graph): List[JObject] = {
    val first = page * PAGE_SIZE
    val last = first + PAGE_SIZE

    val canonicals = graph.V.hasLabel[Canonical].toCC[Canonical]
      .range(first, last).toList

    canonicals.flatMap(canonicalToBlobObject(graph, _))
  }



  def canonicalWithID(canonicalID: UUID)(graph: Graph): Option[JObject] =
    Traversals.canonicalsWithUUID(graph.V, canonicalID)
      .toCC[Canonical]
      .headOption
      .flatMap(canonicalToBlobObject(graph, _))

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
