package org.mediachain

import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._
import scala.util.Random
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

case class QueryObjects(
  person: Person,
  personCanonical: Canonical,
  photoBlob: PhotoBlob,
  photoBlobCanonical: Canonical,
  modifiedPhotoBlob: PhotoBlob
  );

case class QuerySpecContext(graph: Graph, q: QueryObjects)

object QuerySpec extends Specification with ForEach[QuerySpecContext] {
  def setupTree(graph: Graph): QueryObjects = {
    def getPhotoBlob: PhotoBlob = {
      val title = "A Starry Night"
      val desc = "shiny!"
      val date = "2016-02-22T19:04:13+00:00"
      PhotoBlob(None, title, desc, date, None)
    }

    def getModifiedPhotoBlob: PhotoBlob = {
      getPhotoBlob.copy(description = "Stars are pretty...")
    }

    def getPerson: Person = {
      val alex = "Alex Grey"
      Person.create(alex)
    }

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

    QueryObjects(
      Person(personV).get,
      Canonical(personCanonicalV),
      PhotoBlob(photoBlobV).get,
      Canonical(canonicalV),
      PhotoBlob(modifiedBlobV).get)
  }

  // TODO: can you figure out how to abstract out the connection creation?
  def foreach[R: AsResult](f: QuerySpecContext => R): Result = {

    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}").getNoTx()
    try {
      val queryObjects = setupTree(graph)
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
    val queriedPhoto = Query.findPhotoBlob(context.graph, context.q.photoBlob)

    queriedPhoto must beSome[Canonical].which(c =>
      c.canonicalID == context.q.photoBlobCanonical.canonicalID)
  }

  def findsTree = { context: QuerySpecContext =>
    pending
  }

  def findsPerson = { context: QuerySpecContext =>
    val queriedCanonical = Query.findPerson(context.graph, context.q.person)

    queriedCanonical must beSome[Canonical].which(c =>
      c.canonicalID == context.q.personCanonical.canonicalID)
  }

  def findsAuthor = { context: QuerySpecContext =>
    val queriedAuthor = Query.findAuthorForBlob(context.graph, context.q.photoBlob)

    queriedAuthor must beSome[Canonical].which(c =>
      c.canonicalID == context.q.personCanonical.canonicalID)
  }

  def findsWorks = { context: QuerySpecContext =>
    val queriedWorks = Query.findWorks(context.graph, context.q.person)

    queriedWorks must beSome[Seq[Canonical]].which(s =>
      s.contains(context.q.photoBlobCanonical))
  }

  def doesNotFindPhoto = { context: QuerySpecContext =>
    // guarantees returned string is different from input
    // TODO: accept distance
    def mutate(s: String): String = {
      val idx = Random.nextInt(s.length)
      val chars = ('a' to 'z').toSet
      val replaced = s.charAt(idx)
      val replacing = (chars - replaced).toVector(Random.nextInt(chars.size) - 1)
      s.updated(idx, replacing)
    }

    val queryBlob = context.q.photoBlob.copy(
      description = mutate(context.q.photoBlob.description))

    val queriedPhoto = Query.findPhotoBlob(context.graph, queryBlob)

    queriedPhoto must beNone
  }

  def findsCanonicalForModifiedBlob = { context: QuerySpecContext =>

    val parentCanonical = Query.findPhotoBlob(context.graph, context.q.photoBlob)
    val childCanonical = Query.findCanonicalForBlob(context.graph, context.q.modifiedPhotoBlob)
    (childCanonical must beSome[Canonical]) and
      (childCanonical must_== parentCanonical)
  }


}
