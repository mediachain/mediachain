package io.mediachain.translation

import java.io.File
import java.net.URI

import io.mediachain.Types.{ImageBlob, Person}

object SpecResources {
  sealed abstract class Partner {
    val sampleArtworks: List[(File, ImageBlob)]
  }

  def resourceUrl(path: String) = this.getClass.getResource(path)
  def resourceUri(path: String) =
    Option(resourceUrl(path)).map(_.toURI)
    .getOrElse(new URI("file:///does/not/exist"))

  lazy val simpleTestResourceUrl = resourceUrl("/simple-test.json")

  object Tate extends Partner {
    lazy val fixtureDir = new File(resourceUri("/datasets/tate"))

    def sampleDataExists = {
      fixtureDir.exists && fixtureDir.isDirectory
    }

    val sampleArtworks = List(new File(fixtureDir, "/artworks/a/000/a00001-1035.json") ->
      ImageBlob(
       id = None,
       title = "A Figure Bowing before a Seated Old Man with his Arm Outstretched in Benediction. Verso: Indecipherable Sketch",
       description = "Watercolour, ink, chalk and graphite on paper. Verso: graphite on paper",
       date = "date not known",
       author = Some(Person(None, name = "Robert Blake")),
       external_ids = Map("tate:id" -> "1035")))
    }

  object Moma extends Partner {
    lazy val fixtureDir = new File(resourceUri("/datasets/moma"))

    def sampleDataExists = {
      fixtureDir.exists && fixtureDir.isDirectory
    }

    val sampleArtworks = List(new File(resourceUrl("/small-moma-sample.json").toURI) ->
      ImageBlob(
        id = None,
        title = "Three Part Large Animals",
        description = "Polyurethane foam and wire",
        date = "1989",
        author = Some(Person(None, name = "Bruce Nauman")),
        external_ids = Map("moma:MoMANumber" -> "194.1996.a-c")))
  }
}
