package io.mediachain

import io.mediachain.Types._
import gremlin.scala._
import io.mediachain.{Merge => SUT}


object MergeSpec extends BaseSpec
  with ForEachGraph[MergeSpecContext]
{

  def is = sequential ^
  s2"""
  Merging two canonicals:
   - Deprecates old DescribedBy edges $deprecatesDescribedBy
   - Supersedes old Canonical $supersedesChildCanonical
   - Creates new DescribedBy edges from new Canonical to old canonical's root blobs $createsNewDescribedByEdges
  """


  def forEachGraph(graph: Graph) = MergeSpecContext(graph)


  def deprecatesDescribedBy = { context: MergeSpecContext =>
    val duplicatePhotoCanonicalVId = context.objects.duplicatePhotoCanonical
        .id.getOrElse(throw new IllegalStateException("Test fixture has no id"))

    val edges = context.graph.V(duplicatePhotoCanonicalVId)
      .outE(DescribedBy)
      .has(Keys.Deprecated, true)
      .toList

    edges must not be empty
  }

  def supersedesChildCanonical = { context: MergeSpecContext =>
    val duplicatePhotoCanonicalVId = context.objects.duplicatePhotoCanonical
      .id.getOrElse(throw new IllegalStateException("Test fixture has no id"))

    val edges = context.graph.V(duplicatePhotoCanonicalVId)
      .outE(SupersededBy)
      .toList

    edges must not be empty
  }

  def createsNewDescribedByEdges = { context: MergeSpecContext =>
    val photoCanonicalVId = context.base.objects.imageBlobCanonical
      .id.getOrElse(throw new IllegalStateException("Test fixture has no id"))

    val blobs = context.graph.V(photoCanonicalVId)
      .out(DescribedBy)
      .toCC[ImageBlob]
      .toList

    blobs must contain(context.objects.duplicateImageBlob)
  }
}



case class MergeSpecObjects(
  val duplicateImageBlob: ImageBlob,
  val duplicatePhotoCanonical: Canonical
)


case class MergeSpecContext(
  base: GraphFixture.Context,
  objects: MergeSpecObjects) {
  def graph = base.graph
}

object MergeSpecContext {
  def apply(graph: Graph): MergeSpecContext = {
    val base = GraphFixture.Context(graph)
    MergeSpecContext(base, setup(base))
  }

  def setup(context: GraphFixture.Context): MergeSpecObjects = {
    val graph = context.graph
    val photo = context.objects.imageBlob
    val duplicatePhoto =
      photo.copy(id = None, title = GraphFixture.Util.mutate(photo.title))

    val duplicatePhotoCanonical = Canonical.create()

    val duplicatePhotoV = graph + duplicatePhoto
    val duplicatePhotoCanonicalV = graph + duplicatePhotoCanonical
    duplicatePhotoCanonicalV --- DescribedBy --> duplicatePhotoV

    val savedPhoto = duplicatePhotoV.toCC[ImageBlob]
    val savedCanonical = duplicatePhotoCanonicalV.toCC[Canonical]

    SUT.mergeCanonicals(graph, savedCanonical, context.objects.imageBlobCanonical)
    MergeSpecObjects(savedPhoto, savedCanonical)
  }
}
