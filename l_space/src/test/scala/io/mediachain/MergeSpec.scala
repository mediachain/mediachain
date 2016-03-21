package io.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.specs2.Specification
import org.specs2.execute.{Result, AsResult}
import org.specs2.specification.ForEach
import io.mediachain.Types._
import gremlin.scala._
import io.mediachain.{Merge => SUT}


object MergeSpec extends BaseSpec
  with ForEach[MergeSpecContext]
{

  def is =
  s2"""
  Merging two canonicals:
   - Deprecates old DescribedBy edges $deprecatesDescribedBy
   - Supersedes old Canonical $supersedesChildCanonical
   - Creates new DescribedBy edges from new Canonical to old canonical's root blobs $createsNewDescribedByEdges
  """


  def foreach[R: AsResult](f: MergeSpecContext => R): Result = {
    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}")
      .getNoTx()
    try {
      AsResult(f(MergeSpecContext(graph)))
    } finally {
      graph.database().drop()
    }
  }


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
    val photoCanonicalVId = context.base.objects.photoBlobCanonical
      .id.getOrElse(throw new IllegalStateException("Test fixture has no id"))

    val blobs = context.graph.V(photoCanonicalVId)
      .out(DescribedBy)
      .toCC[PhotoBlob]
      .toList

    blobs must contain(context.objects.duplicatePhotoBlob)
  }
}



case class MergeSpecObjects(
  val duplicatePhotoBlob: PhotoBlob,
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
    val photo = context.objects.photoBlob
    val duplicatePhoto =
      photo.copy(id = None, title = GraphFixture.Util.mutate(photo.title))

    val duplicatePhotoCanonical = Canonical.create()

    val duplicatePhotoV = graph + duplicatePhoto
    val duplicatePhotoCanonicalV = graph + duplicatePhotoCanonical
    duplicatePhotoCanonicalV --- DescribedBy --> duplicatePhotoV

    val savedPhoto = duplicatePhotoV.toCC[PhotoBlob]
    val savedCanonical = duplicatePhotoCanonicalV.toCC[Canonical]

    SUT.mergeCanonicals(graph, savedCanonical, context.objects.photoBlobCanonical)
    MergeSpecObjects(savedPhoto, savedCanonical)
  }
}
