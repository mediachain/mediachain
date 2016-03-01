package org.mediachain

import org.specs2.Specification

object TraversalsSpec extends Specification with Orientable {
  import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
  import gremlin.scala._
  import Types._
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
    """


  def findsCanonicalByID = { graph: OrientGraph =>
    val canonical = Canonical.create()
    val canonicalV = graph + canonical

    val queriedVertex = SUT.canonicalsWithID(graph.V, canonical.canonicalID)
      .headOption

    queriedVertex must beSome[Vertex].which { v =>
      v must_== canonicalV
    }
  }

  def findsPersonExact = { graph: OrientGraph =>
    val person = Person(None, "Zaphod Beeblebrox")
    val personV = graph + person

    val queriedVertex = SUT.personBlobsWithExactMatch(graph.V, person)
      .headOption

    queriedVertex must beSome[Vertex].which { v =>
      v must_== personV
    }
  }

  def findsPhotoExact = { graph: OrientGraph =>
    val photo = PhotoBlob(None, "IMG_2012.jpg", "foo", "1/2/1234", None)
    val photoV = graph + photo

    val queriedVertex = SUT.photoBlobsWithExactMatch(graph.V, photo)
      .headOption

    queriedVertex must beSome[Vertex].which { v =>
      v must_== photoV
    }
  }

  def findsRawExact = { graph: OrientGraph =>
    val raw = RawMetadataBlob(None, "So raw!")
    val rawV = graph + raw

    val queriedVertex = SUT.rawMetadataBlobsWithExactMatch(graph.V, raw)
      .headOption

    queriedVertex must beSome[Vertex].which { v =>
      v must_== rawV
    }
  }

  def findsCanonicalForRootBlob = { graph: OrientGraph =>
    val photo = PhotoBlob(None, "IMG_2012.jpg", "foo", "1/2/1234", None)
    val canonical = Ingress.addPhotoBlob(graph, photo)

    val queriedCanonicalID = SUT.photoBlobsWithExactMatch(graph.V, photo)
      .flatMap(SUT.getCanonical)
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedCanonicalID must beSome(canonical.canonicalID)
  }

  def findsCanonicalForRevisedBlob = { graph: OrientGraph =>
    val photo = PhotoBlob(None, "IMG_2012.jpg", "foo", "1/2/1234", None)
    val photoRev = PhotoBlob(None, "Foo at sunset", "foo", "1/2/1234", None)

    val canonical = Ingress.addPhotoBlob(graph, photo)

    val photoV = SUT.photoBlobsWithExactMatch(graph.V, photo).headOption
        .getOrElse(throw new IllegalStateException("Unable to retrieve photo vertex"))

    Ingress.modifyPhotoBlob(graph, photoV, photoRev)

    val photoRevCanonicalID = SUT.photoBlobsWithExactMatch(graph.V, photoRev)
      .flatMap(SUT.getCanonical)
      .value(Canonical.Keys.canonicalID)
      .headOption

    photoRevCanonicalID must beSome(canonical.canonicalID)
  }

  def findsAuthorForPhotoBlob = { graph: OrientGraph =>
    val author = Person(None, "Fooman Bars")
    val photo = PhotoBlob(None, "IMG_2012.jpg", "foo", "1/2/1234", Some(author))

    Ingress.addPhotoBlob(graph, photo)
    val queriedAuthorName = SUT.photoBlobsWithExactMatch(graph.V, photo)
      .flatMap(SUT.getAuthor)
      .value(Canonical.Keys.canonicalID)
      .headOption

    queriedAuthorName must beSome[String]
  }

  def findsRawForBlob = pending

  def findsRootRevision = pending

  def liftsVertex = pending
}
