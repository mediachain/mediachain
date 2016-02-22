package org.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._

object QuerySpec extends Specification with Orientable {

  def is =
    s2"""
        Finds a Person's canonical given a case class  $findsPerson
        Finds a PhotoBlob given a case class $findsPhoto
      """

  def findsPerson = { graph: OrientGraph =>
    val alex = "Alex Grey"
    val p = Person.create(alex)
    val alexCanonical = Ingress.addPerson(graph, p)
    val queriedCanonical = Query.findPerson(graph, p)

    queriedCanonical must beSome[Canonical].which(c => {
      c.canonicalID == alexCanonical.canonicalID
    })
  }

  def findsPhoto = { graph: OrientGraph =>
    // manually insert the photo blob until Ingress.addPhotoBlob is working
    val title = "A Starry Night"
    val desc = "shiny!"
    val date = "2016-02-22T19:04:13+00:00"

    val photoBlob = PhotoBlob(None, title, desc, date, None)
    graph + photoBlob

    val queriedPhoto = Query.findPhotoBlob(graph, photoBlob)

    queriedPhoto must beSome[PhotoBlob].which(p => {
      p.title == photoBlob.title
    })
  }
}
