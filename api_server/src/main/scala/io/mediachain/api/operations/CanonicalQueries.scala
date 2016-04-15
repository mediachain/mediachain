package io.mediachain.api.operations

import java.util.UUID

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Traversals
import io.mediachain.Types._
import io.mediachain.core.GraphError.CanonicalNotFound
import io.mediachain.util.JsonUtils
import org.json4s._


object CanonicalQueries {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}
  import org.json4s.JsonDSL._
  implicit val formats = DefaultFormats

  val PAGE_SIZE = 20

  def blobToJObject(metadataBlob: MetadataBlob): JObject =
    JsonUtils.jsonObjectForHashable(metadataBlob) ~
      ("multiHash" -> metadataBlob.multiHash.base58)


  def vertexToMetadataBlob(vertex: Vertex): Option[MetadataBlob] =
    vertex.label match {
      case "ImageBlob" => Some(vertex.toCC[ImageBlob])
      case "Person" => Some(vertex.toCC[Person])
      case "RawMetadataBlob" => Some(vertex.toCC[RawMetadataBlob])
      case _ => None
    }

  def canonicalToBlobObject(graph: Graph, canonical: Canonical, withRaw: Boolean = false)
  : Option[JObject] = {
    // FIXME: this is a pretty sad n+1 query
    val gs = Traversals.canonicalsWithID(graph.V, canonical.canonicalID)
    val rootBlobOpt: Option[(String, MetadataBlob, Option[RawMetadataBlob], Option[Person])] =
      gs.out(DescribedBy).headOption.flatMap { v: Vertex =>
        val raw = if(withRaw) {
          v.toPipeline.flatMap(_.findRawMetadataXor).toOption
        } else None
        val author = v.label match {
          case "ImageBlob" => v.toPipeline.toOption.flatMap(Traversals.getAuthor(_).out(DescribedBy).toCC[Person].headOption())
          case _ => None
        }
        v.label match {
          case "ImageBlob" => Some((v.label, v.toCC[ImageBlob], raw, author))
          case "Person" => Some((v.label, v.toCC[Person], raw, author))
          case _ => None
        }
      }

    rootBlobOpt
      .map { labelWithBlob =>
        val (label: String, blob: MetadataBlob, raw: Option[RawMetadataBlob], author: Option[Person]) = labelWithBlob
        val blobObject = blobToJObject(blob)

        ("canonicalID" -> canonical.canonicalID) ~
          ("artefact" -> (("type" -> label) ~ blobObject ~ ("author" -> author.map(blobToJObject)))) ~
            ("raw" -> raw.map(blobToJObject))
      }
  }

  def listCanonicals(page: Int)(graph: Graph): List[JObject] = {
    val first = page * PAGE_SIZE
    val last = first + PAGE_SIZE

    val canonicals = graph.V.hasLabel[Canonical]
      .range(first, last).toCC[Canonical].toList

    canonicals.flatMap(canonicalToBlobObject(graph, _))
  }

  def canonicalWithID(canonicalID: UUID, withRaw: Boolean = false)(graph: Graph): Option[JObject] = {
      Traversals.canonicalsWithUUID(graph.V, canonicalID).toCC[Canonical]
        .headOption
        .flatMap(canonicalToBlobObject(graph, _, withRaw))
  }

  def historyForCanonical(canonicalID: UUID)(graph: Graph): Option[JObject] = {
    val treeXor = Traversals.canonicalsWithUUID(graph.V, canonicalID).findSubtreeXor

    val author = StepLabel[Vertex]("author")
    val raw = StepLabel[Vertex]("raw")
    val blob = StepLabel[Vertex]("blob")

    for {
      tree <- treeXor.toOption
      canonicalGS = Traversals.canonicalsWithUUID(tree.V, canonicalID)
      canonical <- canonicalGS.toCC[Canonical].headOption

      revisions = Traversals.describingOrModifyingBlobs(tree.V, canonical).toList
      revisionBlobs = revisions.flatMap(vertexToMetadataBlob)
    } yield {
      val res = tree.V.out(DescribedBy).out(ModifiedBy).as(blob).out(AuthoredBy).as(author).out(TranslatedFrom).as(raw).select((raw, author, blob)).toList
      val revisionsJ = revisionBlobs.map(blobToJObject)
      ("canonicalID" -> canonical.canonicalID) ~
        ("revisions" -> revisionsJ)
    }
  }


  def worksForPersonWithCanonicalID(canonicalID: UUID)(graph: Graph)
  : Option[JObject] = {
    val responseXor = for {
      canonicalV <- Xor.fromOption(
        Traversals.canonicalsWithUUID(graph.V, canonicalID).headOption,
        CanonicalNotFound())

      canonical = canonicalV.toCC[Canonical]

      canonicalJobject <- Xor.fromOption(
        canonicalToBlobObject(graph, canonical),
        CanonicalNotFound())

      canonicalGS <- canonicalV.toPipeline
      worksCanonicals <- canonicalGS.findWorksXor
      worksJobjects = worksCanonicals.flatMap(canonicalToBlobObject(graph, _))
    } yield {
      canonicalJobject ~ ("works" -> worksJobjects)
    }

    responseXor.toOption
  }
}
