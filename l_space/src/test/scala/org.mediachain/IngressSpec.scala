package org.mediachain
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import org.mediachain.Traversals.{GremlinScalaImplicits, VertexImplicits}
import org.specs2.Specification
import gremlin.scala._

object IngressSpec extends Specification with Orientable {

  def is =
    s2"""
        Ingests a PhotoBlob with no Author $ingestsPhoto
        Given a PhotoBlob with an existing Author, doesn't recreate it $findsExistingAuthor
        Given an exact match, don't recreate, only attach RawMetadataBlob $attachesRawMetadata
        Given a new PhotoBlob with a new Author, add new Canonical and new Author $ingestsPhotoBothNew
      """

  def ingestsPhoto = { graph: OrientGraph =>
    val photoBlob = PhotoBlob(None, "A Starry Night", "shiny!", "1/2/2013", None)

    val canonical = Ingress.addPhotoBlob(graph, photoBlob)
    canonical.id must beSome[ElementID]
  }

  def findsExistingAuthor = { graph: OrientGraph =>
    val photoBlobs = List(
      PhotoBlob(None, "A Starry Night", "shiny!", "1/2/2013",
        Some(Person(None, "Fooman Bars"))),
      PhotoBlob(None, "A Starrier Night", "shiny!", "1/2/2013",
        Some(Person(None, "Fooman Bars")))
    )

    photoBlobs.foreach(Ingress.addPhotoBlob(graph, _))
    graph.commit

    val people = graph.V.hasLabel[Person].toCC[Person].toList
    people.size shouldEqual 1

    val person = people.head
    person.name shouldEqual "Fooman Bars"
    person.id should beSome

    val photos = graph.V(person.id.get)
      .in(DescribedBy)
      .in(AuthoredBy)
      .toCC[PhotoBlob].toList()

    photos.size shouldEqual 2

    val photoTitles = photos.map(_.title)
    photoTitles must contain("A Starry Night")
    photoTitles must contain("A Starrier Night")
  }

  def attachesRawMetadata = {graph: OrientGraph =>
    val rawString =
      """{"title": "The Last Supper",
          "description: "Why is everyone sitting on the same side of the table?",
          "date": "c. 1495",
          "author": "Leonardo da Vinci"}""".stripMargin
    val raw = RawMetadataBlob(None, rawString)
    val leo = Person(None, "Leonardo da Vinci")
    val blob = PhotoBlob(None,
      "The Last Supper",
      "Why is everyone sitting on the same side of the table?",
      "c. 1495",
      Some(leo))

    // First add without raw metadata
    Ingress.addPhotoBlob(graph, blob)

    // add again with raw metadata
    Ingress.addPhotoBlob(graph, blob, Some(raw))
    graph.commit()

    // These should both == 1, since adding again doesn't recreate the blob vertices
    val photoBlobCount = Traversals.photoBlobsWithExactMatch(graph.V, blob).count.head
    val authorCount = Traversals.personBlobsWithExactMatch(graph.V, leo).count.head

    val photoV = Traversals.photoBlobsWithExactMatch(graph.V, blob)
      .headOption.getOrElse(throw new IllegalStateException("Unable to retrieve photo blob"))
    val authorV = Traversals.personBlobsWithExactMatch(graph.V, leo)
      .headOption.getOrElse(throw new IllegalStateException("Unable to retrieve author blob"))

    val photoRawMeta = photoV.lift.findRawMetadataOption
    val authorRawMeta = authorV.lift.findRawMetadataOption

    (photoBlobCount must_== 1) and
      (authorCount must_== 1) and
      (authorRawMeta must beSome[RawMetadataBlob]) and
      (photoRawMeta must beSome[RawMetadataBlob].which { photoRaw =>
        photoRaw.blob == rawString &&
          photoRaw == authorRawMeta.get
      })
  }

  def ingestsPhotoBothNew = pending
}
