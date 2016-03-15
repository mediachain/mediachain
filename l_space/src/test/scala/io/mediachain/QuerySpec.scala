package io.mediachain

import io.mediachain.Types._
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
  modifiedPhotoBlob: PhotoBlob,
  extraPhotoBlob: PhotoBlob,
  extraPhotoBlobCanonical: Canonical
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

    val stuffI = Random.shuffle(List("can of peas",
      "wishbone", "pair of glasses", "spool of wire", "wrench", "baseball hat", "television", "food",
      "wallet", "jar of pickles", "tea cup", "sketch pad", "towel", "game CD", "steak knife", "slipper",
      "pants", "sand paper", "boom box", "plush unicorn")).toIterator
    val foodI = Random.shuffle(List("Preserved Peaches", "Brussels Sprouts", "Bananas", "Lettuce Salad",
      "Olives", "Broiled Ham", "Cigars", "Mixed Green Salad", "Oyster Bay Asparagus", "Roast Lamb, Mint Sauce",
      "Lemonade", "Consomme en Tasse", "Liqueurs", "Iced Tea", "Canadian Club", "Radis", "Escarole Salad",
      "Preserved figs", "Potatoes, baked", "Macedoine salad")).toIterator
    def getPhotoBlob: PhotoBlob = {
      val title = stuffI.next
      val desc = foodI.next
      // FIXME: randomize date
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

      // add decoy objects that we shouldn't see in a subtree
      val extraPhotoBlob = getPhotoBlob
      val extraPhotoBlobV = graph + extraPhotoBlob
      val extraPhotoBlobCanonical = Canonical.create
      val extraPhotoBlobCanonicalV = graph + extraPhotoBlobCanonical
      extraPhotoBlobCanonicalV --- DescribedBy --> extraPhotoBlobV
      extraPhotoBlobV --- AuthoredBy --> personCanonicalV

      QueryObjects(
        Person(personV).get,
        Canonical(personCanonicalV),
        PhotoBlob(photoBlobV).get,
        Canonical(canonicalV),
        PhotoBlob(modifiedBlobV).get,
        PhotoBlob(extraPhotoBlobV).get,
        Canonical(extraPhotoBlobCanonicalV))
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
    tree.foreach(t => println(t.V.toList()))
    tree must beRightXor { (g: Graph) =>
      // Canonical itself
      (context.q.photoBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "canonical" must beSome) and
      // PhotoBlob
      (context.q.photoBlob.id.flatMap(id => g.V(id).headOption) aka "describing photoblob" must beSome) and
      // Modifying PhotoBlob
      (context.q.modifiedPhotoBlob.id.flatMap(id => g.V(id).headOption) aka "modifying photoblob" must beSome) and
      // Person
      (context.q.person.id.flatMap(id => g.V(id).headOption) aka "person" must beSome) and
      // Person canonical
      (context.q.personCanonical.id.flatMap(id => g.V(id).headOption) aka "person canonical" must beSome) and
      // Another photoblob by same author
      (context.q.extraPhotoBlob.id.flatMap(id => g.V(id).headOption) aka "extra photoblob" must beNone) and
      // Another photoblob's canonical
      (context.q.extraPhotoBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "extra canonical" must beNone)
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
