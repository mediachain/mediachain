package io.mediachain


import gremlin.scala._
import Types._

object TraversalsSpec extends BaseSpec
  with ForEachGraph[GraphFixture.Context] {
  import io.mediachain.{Traversals => SUT}, SUT.Implicits._

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
  """

  def forEachGraph(graph: Graph) = GraphFixture.Context(graph)

  def findsCanonicalByID = { context: GraphFixture.Context =>
    val queriedCanonicalID = context.graph.V ~>
        SUT.canonicalsWithID(context.objects.personCanonical.canonicalID) ~>
      (_.value(Canonical.Keys.canonicalID)) >>
      (_.headOption)

    queriedCanonicalID must beSome(context.objects.personCanonical.canonicalID)
  }

  def findsPersonExact = { context: GraphFixture.Context =>
    val queriedPersonId = context.graph.V ~>
      SUT.personBlobsWithExactMatch(context.objects.person) ~>
      (_.id) >>
      (_.headOption)

    queriedPersonId must beSome(context.objects.person.id.get)
  }

  def findsPhotoExact = { context: GraphFixture.Context =>
    val queriedPhotoId = context.graph.V ~>
      SUT.imageBlobsWithExactMatch(context.objects.imageBlob) ~>
      (_.id) >>
      (_.headOption)

    queriedPhotoId must beSome(context.objects.imageBlob.id.get)
  }

  def findsRawExact = { context: GraphFixture.Context =>
    val rawBlobText = context.graph.V ~>
      SUT.rawMetadataBlobsWithExactMatch(context.objects.rawMetadataBlob) ~>
      (_.value(RawMetadataBlob.Keys.blob)) >>
      (_.headOption)

    rawBlobText must beSome(context.objects.rawMetadataBlob.blob)
  }

  def findsCanonicalForRootBlob = { context: GraphFixture.Context =>
    val queriedCanonicalID = context.graph.V ~>
      SUT.imageBlobsWithExactMatch(context.objects.imageBlob) ~>
      SUT.getCanonical ~>
      (_.value(Canonical.Keys.canonicalID)) >>
      (_.headOption)

    queriedCanonicalID must
      beSome(context.objects.imageBlobCanonical.canonicalID)
  }

  def findsCanonicalForRevisedBlob = { context: GraphFixture.Context =>
    val photoRevCanonicalID = context.graph.V ~>
      SUT.imageBlobsWithExactMatch(context.objects.modifiedImageBlob) ~>
      SUT.getCanonical ~>
      (_.value(Canonical.Keys.canonicalID)) >>
      (_.headOption)

    photoRevCanonicalID must
      beSome(context.objects.imageBlobCanonical.canonicalID)
  }

  def findsSupersededCanonical = { context: GraphFixture.Context =>
    val queriedCanonicalXor = context.graph.V ~>
      SUT.personBlobsWithExactMatch(context.objects.duplicatePerson) >>
      SUT.findCanonicalXor

    queriedCanonicalXor must beRightXor { canonical: Canonical =>
      canonical.canonicalID must_== context.objects.personCanonical.canonicalID
    }
  }

  def findsMergedCanonicalForBlobs = { context: GraphFixture.Context =>
    val c1 = context.graph.V ~>
      SUT.personBlobsWithExactMatch(context.objects.person) >>
      SUT.findCanonicalXor


    val c2 = context.graph.V ~>
      SUT.personBlobsWithExactMatch(context.objects.duplicatePerson) >>
      SUT.findCanonicalXor

    c1 must beRightXor { c1: Canonical =>
      c2 must beRightXor { c2: Canonical =>
        c1.canonicalID must_== c2.canonicalID
      }
    }
  }

  def findsAuthorForImageBlob = { context: GraphFixture.Context =>
    val queriedAuthorCanonicalID =
      context.graph.V ~>
        SUT.imageBlobsWithExactMatch(context.objects.imageBlob) ~>
        SUT.getAuthor ~>
        (_.value(Canonical.Keys.canonicalID)) >>
        (_.headOption)

    queriedAuthorCanonicalID must beSome(context.objects.personCanonical.canonicalID)
  }

  def findsRawForBlob = { context: GraphFixture.Context =>
    val queriedRawString =
      context.graph.V ~>
        SUT.imageBlobsWithExactMatch(context.objects.imageBlob) ~>
        SUT.getRawMetadataForBlob ~>
        (_.value(RawMetadataBlob.Keys.blob)) >>
        (_.headOption)

    queriedRawString must beSome(context.objects.rawMetadataBlob.blob)
  }

  def findsRootRevision = { context: GraphFixture.Context =>
    val rootRevisionId =
      context.graph.V ~>
        SUT.imageBlobsWithExactMatch(context.objects.modifiedImageBlob) ~>
        SUT.getRootRevision ~>
        (_.id) >>
        (_.headOption)

    rootRevisionId must beSome(context.objects.imageBlob.id.get)
  }
}
