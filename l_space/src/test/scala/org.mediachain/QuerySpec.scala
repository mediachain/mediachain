package org.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._
import scala.util.Random

object QuerySpec extends Specification with Orientable {

  def is =
    s2"""
        Given a MetadataBlob, find the Canonical $findsPhoto
        Given a Canonical, finds full tree $findsTree
        Given a Person, finds the person's Canonical $findsPerson
        Given a MetadataBlob, finds the author Person $findsAuthor
        Given a Person, finds all Canonical that they are the Author of $findsWorks

        Does not find a non-matching PhotoBlob $doesNotFindPhoto
      """

  // UTILS BELOW (TODO: move out)
  def getPhotoBlob: PhotoBlob = {
    val title = "A Starry Night"
    val desc = "shiny!"
    val date = "2016-02-22T19:04:13+00:00"
    PhotoBlob(None, title, desc, date, None)
  }

  def getPerson: Person = {
    val alex = "Alex Grey"
    Person.create(alex)
  }

  // guarantees returned string is different from input
  // TODO: accept distance
  def mutate(s: String): String = {
    val idx = Random.nextInt(s.length)
    val chars = ('a' to 'z').toSet
    val replaced = s.charAt(idx)
    val replacing = (chars - replaced).toVector(Random.nextInt(chars.size))
    s.updated(idx, replacing)
  }

  // TESTS BELOW
  def findsPhoto = { graph: OrientGraph =>
    // manually insert the photo blob until Ingress.addPhotoBlob is working
    val photoBlob = getPhotoBlob
    val photoV = graph + photoBlob
    val canonical = Canonical.create
    val canonicalV = graph + canonical

    canonicalV --- DescribedBy --> photoV

    val queriedPhoto = Query.findPhotoBlob(graph, photoBlob)

    queriedPhoto must beSome[Canonical].which(c => {
      c.canonicalID == canonical.canonicalID
    })
  }

  def findsTree = { graph: OrientGraph =>
    pending
  }

  def findsPerson = { graph: OrientGraph =>
    val p = getPerson
    val alexCanonical = Ingress.addPerson(graph, p)
    val queriedCanonical = Query.findPerson(graph, p)

    queriedCanonical must beSome[Canonical].which(c => {
      c.canonicalID == alexCanonical.canonicalID
    })
  }

  def findsAuthor = { graph: OrientGraph =>
    pending
  }

  def findsWorks = { graph: OrientGraph =>
    pending
  }

  def doesNotFindPhoto = { graph: OrientGraph =>
    val photoBlob = getPhotoBlob
    graph + photoBlob

    val queryBlob = photoBlob.copy(description = mutate(photoBlob.description))

    val queriedPhoto = Query.findPhotoBlob(graph, queryBlob)

    queriedPhoto must beNone
  }
}
