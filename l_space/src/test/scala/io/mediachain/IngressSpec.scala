package io.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import io.mediachain.Types._
import io.mediachain.Traversals._
import io.mediachain.Traversals.Implicits._
import gremlin.scala._
import cats.data.Xor
import core.GraphError._
import io.mediachain.Ingress.BlobAddResult

object IngressSpec extends BaseSpec
  with Orientable {

  def is =
    s2"""
        Ingests a ImageBlob with no Author $ingestsPhoto
        Given a ImageBlob with an existing Author, doesn't recreate it $findsExistingAuthor
        Given an exact match, don't recreate, only attach RawMetadataBlob $attachesRawMetadata
        Given a new ImageBlob with a new Author, add new Canonical and new Author $ingestsPhotoBothNew
        Modifies an existing ImageBlob $modifiesExistingImageBlob
      """

  def ingestsPhoto = { graph: OrientGraph =>
    val imageBlob = ImageBlob(None, "A Starry Night", "shiny!", "1/2/2013")

    val result = Ingress.addImageBlob(graph, imageBlob)
    result must beRightXor { res: BlobAddResult =>
      val canonical = res.canonicalVertex.toCC[Canonical]
      canonical.id must beSome[ElementID]
    }
  }

  def findsExistingAuthor = { graph: OrientGraph =>

    val author = Person(None, "Fooman Bars")
    val bundles = List(
      BlobBundle(
        ImageBlob(None, "A Starry Night", "shiny!", "1/2/2013"),
        BlobBundle.Author(author)),
      BlobBundle(
        ImageBlob(None, "A Starrier Night", "shiny!", "1/2/2013"),
        BlobBundle.Author(author))
    )

    bundles.foreach(Ingress.ingestBlobBundle(graph, _))
    graph.commit()

    val people = graph.V.hasLabel[Person].toCC[Person].toList
    people.size shouldEqual 1

    val person = people.head
    person.name shouldEqual "Fooman Bars"
    person.id should beSome

    val photos = graph.V(person.id.get)
      .in(DescribedBy)
      .in(AuthoredBy)
      .toCC[ImageBlob].toList()

    photos.size shouldEqual 2

    val photoTitles = photos.map(_.title)
    photoTitles must contain("A Starry Night")
    photoTitles must contain("A Starrier Night")
  }

  def attachesRawMetadata = { graph: OrientGraph =>
    val rawString =
      """{"title": "The Last Supper",
          "description: "Why is everyone sitting on the same side of the table?",
          "date": "c. 1495",
          "author": "Leonardo da Vinci"}""".stripMargin
    val raw = RawMetadataBlob(None, rawString)
    val leo = Person(None, "Leonardo da Vinci")
    val blob = ImageBlob(None,
      "The Last Supper",
      "Why is everyone sitting on the same side of the table?",
      "c. 1495")

    // First add without raw metadata
    val bundle = BlobBundle(blob, BlobBundle.Author(leo))
    Ingress.ingestBlobBundle(graph, bundle)

    // add again with raw metadata
    Ingress.ingestBlobBundle(graph, bundle, Some(raw))

    // These should both == 1, since adding again doesn't recreate the blob vertices
    val imageBlobCount = graph.V ~>
      imageBlobsWithExactMatch(blob) >>
      (_.count.head)

    val authorCount = graph.V ~>
      personBlobsWithExactMatch(leo) >>
      (_.count.head)

    val photoV = (graph.V ~> imageBlobsWithExactMatch(blob))
      .headOption.getOrElse(
        throw new IllegalStateException("Unable to retrieve photo blob")
      )

    val authorV = (graph.V ~> personBlobsWithExactMatch(leo))
      .headOption.getOrElse(
        throw new IllegalStateException("Unable to retrieve author blob")
      )

    val photoRawMeta = photoV.toPipeline.flatMap(_ >> findRawMetadataXor)
    val authorRawMeta = authorV.toPipeline.flatMap(_ >> findRawMetadataXor)
    val photoMatch = photoRawMeta match {
      case Xor.Right(photo) => photo.blob == rawString
      case _ => false
    }
    val authorMatch = photoRawMeta.toList ++ authorRawMeta.toList match {
      case List(photo, author) => photo == author
      case _ => false
    }

    imageBlobCount must_== 1
    photoRawMeta.isRight must beTrue
    photoMatch must beTrue
  }

  def ingestsPhotoBothNew = pending

  def modifiesExistingImageBlob = { graph: OrientGraph =>
    val objs = GraphFixture.Util.setupTree(graph)
    val currentHead = objs.modifiedImageBlob
    val newPhoto = currentHead.copy(id = None,
      title = GraphFixture.Util.mutate(currentHead.title))

    val currentHeadV = currentHead.vertex(graph)
        .getOrElse(throw new IllegalStateException(
          "Test fixture can't be retrieved from graph"))

    val resultCanonical = Ingress.modifyImageBlob(graph, currentHeadV, newPhoto)

    resultCanonical must beRightXor { res: BlobAddResult =>
      res.canonicalVertex must not beNull
    }
  }
}
