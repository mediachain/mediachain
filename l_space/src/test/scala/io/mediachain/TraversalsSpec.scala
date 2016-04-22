package io.mediachain


import gremlin.scala._
import Types._

object TraversalsSpec extends BaseSpec
  with ForEachGraph[GraphFixture.Context] {
  import io.mediachain.{Traversals => SUT}, SUT.GremlinScalaImplicits

  def is = sequential ^
  s2"""
   Finds a canonical vertex given a canonicalID $findsCanonicalByID
   Finds a person vertex exactly matching a Person CC $findsPersonExact
   Finds a photo blob vertex exactly matching a ImageBlob CC $findsPhotoExact
   Finds a raw metadata vertex exactly matching a RawMetadataBlob CC $findsRawExact

   Finds the canonical vertex for a blob vertex $findsCanonicalForRootBlob
   Finds the canonical vertex for a revised blob vertex $findsCanonicalForRevisedBlob
   Finds the new canonical for a superseded canonical $findsSupersededCanonical
   Finds the same canonical for blobs of merged canonicals $findsMergedCanonicalForBlobs
   Finds the author vertex for a photo blob vertex $findsAuthorForImageBlob
   Finds the raw metadata vertex for a blob vertex $findsRawForBlob
   Finds the root revision of a blob vertex $findsRootRevision

   Extends GremlinScala with implicits:
    Finds canonical for blob vertex and converts to Canonical CC $findsCanonicalImplicit
    Finds author for blob vertex and converts to Canonical CC $findsAuthorImplicit
    Finds raw metadata for blob vertex and converts to RawMetadataBlob CC $findsRawImplicit
  """

  def forEachGraph(graph: Graph) = GraphFixture.Context(graph)

  def findsCanonicalByID = { context: GraphFixture.Context =>
    val queriedCanonicalID =
      SUT.canonicalsWithID(
        context.graph.V,
        context.objects.personCanonical.canonicalID
      ).value(Canonical.Keys.canonicalID)
        .headOption

    queriedCanonicalID must beSome(context.objects.personCanonical.canonicalID)
  }

  def findsPersonExact = { context: GraphFixture.Context =>
    val queriedPersonId =
      SUT.personBlobsWithExactMatch(
        context.graph.V,
        context.objects.person
      ).id
      .headOption

    queriedPersonId must beSome(context.objects.person.id.get)
  }

  def findsPhotoExact = { context: GraphFixture.Context =>
    val queriedPhotoId =
        SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob)
          .id
          .headOption

    queriedPhotoId must beSome(context.objects.imageBlob.id.get)
  }

  def findsRawExact = { context: GraphFixture.Context =>
    val rawBlobText =
      SUT.rawMetadataBlobsWithExactMatch(
        context.graph.V,
        context.objects.rawMetadataBlob
      ).value(RawMetadataBlob.Keys.blob).headOption

    rawBlobText must beSome(context.objects.rawMetadataBlob.blob)
  }

  def findsCanonicalForRootBlob = { context: GraphFixture.Context =>
    val queriedCanonicalID = SUT.getCanonical(
      SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob)
    )
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedCanonicalID must
      beSome(context.objects.imageBlobCanonical.canonicalID)
  }

  def findsCanonicalForRevisedBlob = { context: GraphFixture.Context =>
    val photoRevCanonicalID =
      SUT.getCanonical(
        SUT.imageBlobsWithExactMatch(context.graph.V,
          context.objects.modifiedImageBlob)
      )
        .value(Canonical.Keys.canonicalID)
        .headOption

    photoRevCanonicalID must
      beSome(context.objects.imageBlobCanonical.canonicalID)
  }

  def findsSupersededCanonical = { context: GraphFixture.Context =>
    val queriedCanonicalID =
      SUT.personBlobsWithExactMatch(context.graph.V, context.objects.duplicatePerson)
        .findCanonicalXor
        .map(_.canonicalID)

    queriedCanonicalID must beRightXor { id: String =>
      id must_== context.objects.personCanonical.canonicalID
    }
  }

  def findsMergedCanonicalForBlobs = { context: GraphFixture.Context =>
    val c1 = SUT.personBlobsWithExactMatch(context.graph.V, context.objects.person)
        .findCanonicalXor

    val c2 = SUT.personBlobsWithExactMatch(context.graph.V, context.objects.duplicatePerson)
      .findCanonicalXor

    c1 must beRightXor { c1: Canonical =>
      c2 must beRightXor { c2: Canonical =>
        c1.canonicalID must_== c2.canonicalID
      }
    }
  }

  def findsAuthorForImageBlob = { context: GraphFixture.Context =>
    val queriedAuthorCanonicalID =
      SUT.getAuthor(
        SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob))
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedAuthorCanonicalID must beSome(context.objects.personCanonical.canonicalID)
  }

  def findsRawForBlob = { context: GraphFixture.Context =>
    val queriedRawString =
      SUT.getRawMetadataForBlob(
        SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob))
      .value(RawMetadataBlob.Keys.blob)
      .headOption

    queriedRawString must beSome(context.objects.rawMetadataBlob.blob)
  }

  def findsRootRevision = { context: GraphFixture.Context =>
    val rootRevisionId = SUT.getRootRevision(
      SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.modifiedImageBlob)
    )
      .id
      .headOption

    rootRevisionId must beSome(context.objects.imageBlob.id.get)
  }


  def findsCanonicalImplicit = { context: GraphFixture.Context =>
    val revisedPhotoCanonicalID =
      SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob)
      .findCanonicalXor
      .map(_.canonicalID)

    revisedPhotoCanonicalID must beRightXor { x =>
      x mustEqual context.objects.imageBlobCanonical.canonicalID
    }
  }

  def findsAuthorImplicit = { context: GraphFixture.Context =>
    val queriedAuthorCanonicalID =
      SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob)
      .findAuthorXor
      .map(_.canonicalID)

    queriedAuthorCanonicalID must beRightXor { x =>
      x mustEqual context.objects.personCanonical.canonicalID
    }
  }

  def findsRawImplicit = { context: GraphFixture.Context =>
    val queriedRawString =
      SUT.imageBlobsWithExactMatch(context.graph.V, context.objects.imageBlob)
      .findRawMetadataXor
      .map(_.blob)

    queriedRawString must beRightXor { x =>
      x mustEqual context.objects.rawMetadataBlob.blob
    }
  }
}
