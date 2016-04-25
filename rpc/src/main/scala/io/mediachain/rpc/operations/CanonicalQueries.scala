package io.mediachain.rpc.operations

import java.util.UUID

import cats.data.Xor
import gremlin.scala._
import io.mediachain.Types._
import io.mediachain.core.GraphError.CanonicalNotFound
import io.mediachain.rpc.TypeConversions._
import io.mediachain.protos.Services._
import io.mediachain.Traversals, Traversals._, Traversals.Implicits._


object CanonicalQueries {

  val PAGE_SIZE = 20

  // TODO: Move this to io.mediachain.Types, use vals instead of string literals
  def vertexToMetadataBlob(vertex: Vertex): Option[MetadataBlob] =
    vertex.label match {
      case "ImageBlob" => Some(vertex.toCC[ImageBlob])
      case "Person" => Some(vertex.toCC[Person])
      case "RawMetadataBlob" => Some(vertex.toCC[RawMetadataBlob])
      case _ => None
    }

  def canonicalWithRootRevision(canonical: Canonical, withRaw: Boolean = false)(graph: Graph)
  : Option[CanonicalWithRootRevision] = {
    // FIXME - use decent graph traversal
    val gs = graph.V ~> canonicalsWithID(canonical.canonicalID)
    val rootBlobOpt: Option[(MetadataBlob, Option[RawMetadataBlob], Option[Person])] =
      gs.out(DescribedBy).headOption.flatMap { v: Vertex =>
        val raw = if(withRaw) {
          v.toPipeline.flatMap(_ >> findRawMetadataXor).toOption
        } else None
        val author = v.label match {
          case "ImageBlob" => v.toPipeline.toOption.flatMap(Traversals.getAuthor(_).out(DescribedBy).toCC[Person].headOption())
          case _ => None
        }
        v.label match {
          case "ImageBlob" => Some((v.toCC[ImageBlob], raw, author))
          case "Person" => Some((v.toCC[Person], raw, author))
          case _ => None
        }
      }

    rootBlobOpt
      .map { blobWithRelated =>
        val (blob: MetadataBlob, raw: Option[RawMetadataBlob], author: Option[Person]) = blobWithRelated
        val rpcBlob = metadataBlobToRPC(blob)

        CanonicalWithRootRevision(rawMetadata = raw.map(_.toRPC), author = author.map(_.toRPC))
          .withCanonicalID(canonical.canonicalID)
          .withRootRevision(rpcBlob)
      }
  }


  def listCanonicals(page: Int)(graph: Graph)
  : CanonicalList = {
    val first = page * PAGE_SIZE
    val last = first + PAGE_SIZE

    val canonicals = graph.V.hasLabel[Canonical]
      .range(first, last).toCC[Canonical].toList

    val withRootRevs = canonicals.flatMap(canonicalWithRootRevision(_)(graph))
    CanonicalList().withCanonicals(withRootRevs)
  }

  def canonicalWithID(canonicalID: UUID, withRaw: Boolean = false)(graph: Graph)
  : Option[CanonicalWithRootRevision] = {
    (graph.V ~> canonicalsWithUUID(canonicalID))
      .toCC[Canonical]
      .headOption
      .flatMap(canonicalWithRootRevision(_, withRaw)(graph))
  }

  def historyForCanonical(canonicalID: String)(graph: Graph)
  : Option[CanonicalWithHistory] = {
    val treeXor = graph.V ~> canonicalsWithID(canonicalID) >> findSubtreeXor

    for {
      tree <- treeXor.toOption
      canonicalGS = tree.V ~> canonicalsWithID(canonicalID)
      canonical <- canonicalGS.toCC[Canonical].headOption

      revisions = (tree.V ~> describingOrModifyingBlobs(canonical)).toList
      revisionBlobs = revisions.flatMap(vertexToMetadataBlob)
    } yield {
      val revisions = revisionBlobs.map(metadataBlobToRPC)
      CanonicalWithHistory()
        .withCanonicalID(canonical.canonicalID)
        .withRevisions(revisions)
    }
  }


  def worksForPersonWithCanonicalID(canonicalID: String)(graph: Graph)
  : Option[WorksForAuthor] = {
    val responseXor = for {
      canonicalV <- graph.V ~> canonicalsWithID(canonicalID) >> headXor

      canonical = canonicalV.toCC[Canonical]

      canonicalRPC <- Xor.fromOption(
        canonicalWithRootRevision(canonical)(graph),
        CanonicalNotFound())

      canonicalGS <- canonicalV.toPipeline
      worksCanonicals <- canonicalGS >> findWorksXor
      worksWithRootRev = worksCanonicals.flatMap(canonicalWithRootRevision(_)(graph))
    } yield {
      WorksForAuthor()
        .withAuthor(canonicalRPC)
        .withWorks(worksWithRootRev)
    }

    responseXor.toOption
  }
}
