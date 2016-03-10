package org.mediachain


import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.specs2.Specification
import gremlin.scala._
import Types._
import org.specs2.execute.{Result, AsResult}
import org.specs2.specification.ForEach

class TraversalsFixtures(graph: Graph) {
  def g = graph

  val rawZaphod = RawMetadataBlob(None, """{"name": "Zaphod Beeblebrox}""")
  val zaphod = Person(None, "Zaphod Beeblebrox")
  val photo = PhotoBlob(None, "IMG_2012.jpg", "foo", "1/2/1234", Some(zaphod))
  val revisedPhoto = PhotoBlob(None, "Foo at sunset", "foo", "1/2/1234", Some(zaphod))

  val rawZaphodVertex = graph + rawZaphod
  val zaphodVertex = graph + zaphod
  val photoVertex = graph + photo
  val revisedPhotoVertex = graph + revisedPhoto

  val zaphodCanonical = Canonical.create()
  val photoCanonical = Canonical.create()

  val zaphodCanonicalVertex = graph + zaphodCanonical
  val photoCanonicalVertex = graph + photoCanonical

  zaphodCanonicalVertex --- DescribedBy --> zaphodVertex
  photoCanonicalVertex --- DescribedBy --> photoVertex
  photoVertex --- ModifiedBy --> revisedPhotoVertex
  photoVertex --- AuthoredBy --> zaphodCanonicalVertex
  zaphodVertex --- TranslatedFrom --> rawZaphodVertex
}

object TraversalsSpec extends
  Specification with
  ForEach[TraversalsFixtures] with
  XorMatchers {
  import org.mediachain.{Traversals => SUT}, SUT.GremlinScalaImplicits, SUT.VertexImplicits

  def is =
    s2"""
       Finds a canonical vertex given a canonicalID: $findsCanonicalByID
       Finds a person vertex exactly matching a Person CC: $findsPersonExact
       Finds a photo blob vertex exactly matching a PhotoBlob CC: $findsPhotoExact
       Finds a raw metadata vertex exactly matching a RawMetadataBlob CC: $findsRawExact

       Finds the canonical vertex for a blob vertex: $findsCanonicalForRootBlob
       Finds the canonical vertex for a revised blob vertex: $findsCanonicalForRevisedBlob
       Finds the author vertex for a photo blob vertex: $findsAuthorForPhotoBlob
       Finds the raw metadata vertex for a blob vertex: $findsRawForBlob
       Finds the root revision of a blob vertex: $findsRootRevision

       Lifts a vertex into a gremlin-scala query pipeline: $liftsVertex

       Extends GremlinScala with implicts:
        Finds canonical for blob vertex and converts to Canonical CC: $findsCanonicalImplicit
        Finds author for blob vertex and converts to Canonical CC: $findsAuthorImplicit
        Finds raw medatata for blob vertex and converts to RawMetadataBlob CC: $findsRawImplicit
    """

  // TODO: can you figure out how to abstract out the connection creation?
  def foreach[R: AsResult](f: TraversalsFixtures => R): Result = {

    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}").getNoTx()
    try {
      val fixtures = new TraversalsFixtures(graph)
      AsResult(f(fixtures))
    } finally {
      graph.database().drop()
    }
  }

  def findsCanonicalByID = { fixtures: TraversalsFixtures =>
    val queriedVertex = SUT.canonicalsWithID(fixtures.g.V, fixtures.zaphodCanonical.canonicalID)
      .headOption

    queriedVertex must beSome(fixtures.zaphodCanonicalVertex)
  }

  def findsPersonExact = { fixtures: TraversalsFixtures =>
    val queriedVertex = SUT.personBlobsWithExactMatch(fixtures.g.V, fixtures.zaphod)
      .headOption

    queriedVertex must beSome(fixtures.zaphodVertex)
  }

  def findsPhotoExact = { fixtures: TraversalsFixtures =>
    val queriedVertex = SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.photo)
      .headOption

    queriedVertex must beSome(fixtures.photoVertex)
  }

  def findsRawExact = { fixtures: TraversalsFixtures =>
    val queriedVertex = SUT.rawMetadataBlobsWithExactMatch(fixtures.g.V, fixtures.rawZaphod)
      .headOption

    queriedVertex must beSome(fixtures.rawZaphodVertex)
  }

  def findsCanonicalForRootBlob = { fixtures: TraversalsFixtures =>
    val queriedCanonicalID = SUT.getCanonical(SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.photo))
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedCanonicalID must beSome(fixtures.photoCanonical.canonicalID)
  }

  def findsCanonicalForRevisedBlob = { fixtures: TraversalsFixtures =>
    val photoRevCanonicalID = SUT.getCanonical(SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.revisedPhoto))
      .value(Canonical.Keys.canonicalID)
      .headOption

    photoRevCanonicalID must beSome(fixtures.photoCanonical.canonicalID)
  }

  def findsAuthorForPhotoBlob = { fixtures: TraversalsFixtures =>
    val queriedAuthorCanonicalID = SUT.getAuthor(SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.photo))
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedAuthorCanonicalID must beSome(fixtures.zaphodCanonical.canonicalID)
  }

  def findsRawForBlob = { fixtures: TraversalsFixtures =>
    val queriedRawString = SUT.getRawMetadataForBlob(SUT.personBlobsWithExactMatch(fixtures.g.V, fixtures.zaphod))
      .value(RawMetadataBlob.Keys.blob)
      .headOption

    queriedRawString must beSome(fixtures.rawZaphod.blob)
  }

  def findsRootRevision = { fixtures: TraversalsFixtures =>
    val rootRevV = SUT.getRootRevision(SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.revisedPhoto))
      .headOption

    rootRevV must beSome(fixtures.photoVertex)
  }

  def liftsVertex = { fixtures: TraversalsFixtures =>
    fixtures.photoVertex.lift.headOption must beSome(fixtures.photoVertex)
  }

  def findsCanonicalImplicit = { fixtures: TraversalsFixtures =>
    val revisedPhotoCanonicalID = SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.revisedPhoto)
      .findCanonicalXor
      .map(_.canonicalID)

    revisedPhotoCanonicalID must beRightXor { x =>
      x mustEqual fixtures.photoCanonical.canonicalID
    }
  }

  def findsAuthorImplicit = { fixtures: TraversalsFixtures =>
    val queriedAuthorCanonicalID = SUT.photoBlobsWithExactMatch(fixtures.g.V, fixtures.photo)
      .findAuthorXor
      .map(_.canonicalID)

    queriedAuthorCanonicalID must beRightXor { x =>
      x mustEqual fixtures.zaphodCanonical.canonicalID
    }
  }

  def findsRawImplicit = { fixtures: TraversalsFixtures =>
    val queriedRawString = SUT.personBlobsWithExactMatch(fixtures.g.V, fixtures.zaphod)
      .findRawMetadataXor
      .map(_.blob)

    queriedRawString must beRightXor { x =>
      x mustEqual fixtures.rawZaphod.blob
    }
  }
}
