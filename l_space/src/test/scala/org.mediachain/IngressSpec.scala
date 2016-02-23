package org.mediachain
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._

object IngressSpec extends Specification with Orientable {

  def is =
    s2"""
        Ingests a PhotoBlob: $ingestsPhoto
      """

  def ingestsPhoto = { graph: OrientGraph =>
    val photoBlob = PhotoBlob(None, "A Starry Night", "shiny!", "1/2/2013", None)

    val canonical = Ingress.addPhotoBlob(graph, photoBlob)
    canonical.id must beSome[String]
  }
}
