package org.mediachain

import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._
import scala.util.Random
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

case class QueryObjects(
  person: Person,
  personCanonical: Canonical,
  photoBlob: PhotoBlob,
  photoBlobCanonical: Canonical,
  modifiedPhotoBlob: PhotoBlob
  )

case class QuerySpecContext(graph: Graph, q: QueryObjects)

object QuerySpec extends
  Specification with
  ForEach[QuerySpecContext] with
  XorMatchers {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}
  object Util {
    // guarantees returned string is different from input
    // TODO: accept distance
    def mutate(s: String): String = {
      val idx = Random.nextInt(s.length)
      val chars = ('a' to 'z').toSet
      val replaced = s.charAt(idx)
      val replacing = (chars - replaced).toVector(Random.nextInt(chars.size - 1))
      s.updated(idx, replacing)
    }

    def getPhotoBlob: PhotoBlob = {
      val title = "A Starry Night"
      val desc = "shiny!"
      val date = "2016-02-22T19:04:13+00:00"
      PhotoBlob(None, title, desc, date, None)
    }

    def getModifiedPhotoBlob: PhotoBlob = {
      val b = getPhotoBlob
      b.copy(description = mutate(b.description))
    }

    val bodhisattvasI = Random.shuffle(List("Avalokitesvara",
      "Manjushri", "Samantabhadra", "Kshitigarbha", "Maitreya", "Mahasthamaprapta", "Ākāśagarbha")).toIterator
    def getPerson: Person = Person.create(bodhisattvasI.next)

    def setupTree(graph: Graph): QueryObjects = {
      // add photo and canonical
      val photoBlob = getPhotoBlob
      val photoBlobV = graph + photoBlob
      val photoBlobCanonical = Canonical.create
      val canonicalV = graph + photoBlobCanonical
      canonicalV --- DescribedBy --> photoBlobV

      // add a revision to a photo
      val modifiedBlob = getModifiedPhotoBlob
      val modifiedBlobV = graph + modifiedBlob
      photoBlobV --- ModifiedBy --> modifiedBlobV

      // add an author for the photo
      val person = getPerson
      val personV = graph + person
      val personCanonical = Canonical.create
      val personCanonicalV = graph + personCanonical
      personCanonicalV --- DescribedBy --> personV
      photoBlobV --- AuthoredBy --> personCanonicalV

      // add decoy objects that we shouldn't see
      // val extraPerson = getPerson
      // val extraCanonical = ...

      QueryObjects(
        Person(personV).get,
        Canonical(personCanonicalV),
        PhotoBlob(photoBlobV).get,
        Canonical(canonicalV),
        PhotoBlob(modifiedBlobV).get)
    }
  }

  // TODO: can you figure out how to abstract out the connection creation?
  def foreach[R: AsResult](f: QuerySpecContext => R): Result = {

    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}").getNoTx()
    try {
      val queryObjects = Util.setupTree(graph)
      AsResult(f(QuerySpecContext(graph, queryObjects)))
    } finally {
      graph.database().drop()
    }
  }

  def is =
  s2"""
  Given a MetadataBlob, find the Canonical $findsPhoto
  Given a Canonical, finds full tree $findsTree
  Given a Person, finds the person's Canonical $findsPerson
  Given a MetadataBlob, finds the author Person $findsAuthor
  Given a Person, finds all Canonical that they are the Author of $findsWorks

  Does not find a non-matching PhotoBlob $doesNotFindPhoto
  """

  // TESTS BELOW
  def findsPhoto = { context: QuerySpecContext =>
    val queriedCanonical = Query.findPhotoBlob(context.graph, context.q.photoBlob)

    queriedCanonical must beRightXor({ (c: Canonical) =>
      c.canonicalID == context.q.photoBlobCanonical.canonicalID
    })
  }

  def findsTree = { context: QuerySpecContext =>
    val tree = Query.findTreeForCanonical(context.graph, context.q.photoBlobCanonical)
    tree must beRightXor { (g: Graph) =>
      (g.V(context.q.photoBlob.id.get).findCanonicalXor must beRightXor) and
      (g.V(context.q.person.id.get).findCanonicalXor must beRightXor)
    }
  }

  def findsPerson = { context: QuerySpecContext =>
    val queriedCanonical = Query.findPerson(context.graph, context.q.person)

    queriedCanonical must beRightXor { (person: Canonical) =>
      person.canonicalID == context.q.personCanonical.canonicalID &&
        person.id.isDefined
    }
  }

  def findsAuthor = { context: QuerySpecContext =>
    val queriedAuthor =
      Query.findAuthorForBlob(context.graph, context.q.photoBlob)

    queriedAuthor must beRightXor { (c: Canonical) =>
      c.canonicalID == context.q.personCanonical.canonicalID
    }
  }

  def findsWorks = { context: QuerySpecContext =>
    val queriedWorks = Query.findWorks(context.graph, context.q.person)

    queriedWorks must beRightXor { (s: List[Canonical]) =>
      s.contains(context.q.photoBlobCanonical)
    }
  }

  def doesNotFindPhoto = { context: QuerySpecContext =>
   val queryBlob = context.q.photoBlob.copy(
      description = Util.mutate(context.q.photoBlob.description))

    val queriedPhoto = Query.findPhotoBlob(context.graph, queryBlob)

    queriedPhoto must beLeftXor()
  }

  def findsCanonicalForModifiedBlob = { context: QuerySpecContext =>

    val parentCanonical = Query.findPhotoBlob(context.graph, context.q.photoBlob)
    val childCanonical = Query.findCanonicalForBlob(context.graph, context.q.modifiedPhotoBlob)
    (childCanonical must beRightXor()) and
      (childCanonical must_== parentCanonical)
  }


}
