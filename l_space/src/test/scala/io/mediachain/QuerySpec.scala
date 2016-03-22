package io.mediachain

import io.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._
import scala.util.Random
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

object QuerySpec extends BaseSpec
  with ForEach[GraphFixture.Context] {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}


  // TODO: can you figure out how to abstract out the connection creation?
  def foreach[R: AsResult](f: GraphFixture.Context => R): Result = {

    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}").getNoTx()
    try {
      AsResult(f(GraphFixture.Context(graph)))
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
  def findsPhoto = { context: GraphFixture.Context =>
    val queriedCanonical = Query.findPhotoBlob(context.graph, context.objects.photoBlob)

    queriedCanonical must beRightXor({ (c: Canonical) =>
      c.canonicalID == context.objects.photoBlobCanonical.canonicalID
    })
  }

  def findsTree = { context: GraphFixture.Context =>
    val tree = Query.findTreeForCanonical(context.graph, context.objects.photoBlobCanonical)
    tree.foreach(t => println(t.V.toList()))
    tree must beRightXor { (g: Graph) =>
      // Canonical itself
      (context.objects.photoBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "canonical" must beSome) and
      // PhotoBlob
      (context.objects.photoBlob.id.flatMap(id => g.V(id).headOption) aka "describing photoblob" must beSome) and
      // Modifying PhotoBlob
      (context.objects.modifiedPhotoBlob.id.flatMap(id => g.V(id).headOption) aka "modifying photoblob" must beSome) and
      // Person
      (context.objects.person.id.flatMap(id => g.V(id).headOption) aka "person" must beSome) and
      // Person canonical
      (context.objects.personCanonical.id.flatMap(id => g.V(id).headOption) aka "person canonical" must beSome) and
      // Another photoblob by same author
      (context.objects.extraPhotoBlob.id.flatMap(id => g.V(id).headOption) aka "extra photoblob" must beNone) and
      // Another photoblob's canonical
      (context.objects.extraPhotoBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "extra canonical" must beNone)
    }
  }

  def findsPerson = { context: GraphFixture.Context =>
    val queriedCanonical = Query.findPerson(context.graph, context.objects.person)

    queriedCanonical must beRightXor { (person: Canonical) =>
      (person.canonicalID must_== context.objects.personCanonical.canonicalID) and
        (person.id must beSome)
    }
  }

  def findsAuthor = { context: GraphFixture.Context =>
    val queriedAuthor =
      Query.findAuthorForBlob(context.graph, context.objects.photoBlob)

    queriedAuthor must beRightXor { (c: Canonical) =>
      c.canonicalID must_== context.objects.personCanonical.canonicalID
    }
  }

  def findsWorks = { context: GraphFixture.Context =>
    val queriedWorks = Query.findWorks(context.graph, context.objects.person)

    queriedWorks must beRightXor { (s: List[Canonical]) =>
      s must contain(context.objects.photoBlobCanonical)
    }
  }

  def doesNotFindPhoto = { context: GraphFixture.Context =>
   val queryBlob = context.objects.photoBlob.copy(
      description = GraphFixture.Util.mutate(context.objects.photoBlob.description))

    val queriedPhoto = Query.findPhotoBlob(context.graph, queryBlob)

    queriedPhoto must beLeftXor()
  }

  def findsCanonicalForModifiedBlob = { context: GraphFixture.Context =>

    val parentCanonical = Query.findPhotoBlob(context.graph, context.objects.photoBlob)
    val childCanonical = Query.findCanonicalForBlob(context.graph, context.objects.modifiedPhotoBlob)
    (childCanonical must beRightXor) and
      (childCanonical must_== parentCanonical)
  }


}
