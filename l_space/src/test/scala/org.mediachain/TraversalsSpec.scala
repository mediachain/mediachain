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

       Finds the canonical vertex for a blob vertex: $findsCanonicalForBlob
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

  def findsCanonicalForBlob = pending

  def findsAuthorForPhotoBlob = pending

  def findsRawForBlob = pending

  def findsRootRevision = pending

  def liftsVertex = pending
}
