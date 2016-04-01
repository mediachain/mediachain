package io.mediachain.translation

import java.io.File

import io.mediachain.Types.{ImageBlob, Person}

object SpecResources {
  sealed abstract class Partner {
    val sampleArtworks: List[(File, ImageBlob)]
  }

  def resourceUrl(path: String) = this.getClass.getResource(path)

  lazy val simpleTestResourceUrl = resourceUrl("/simple-test.json")

  object Tate extends Partner {
    lazy val fixtureDir = new File(resourceUrl("/datasets/tate").toURI)

    def sampleDataExists = {
      fixtureDir.exists && fixtureDir.isDirectory
    }

    val sampleArtworks = List((new File(fixtureDir, "/artworks/a/000/a00001-1035.json") ->
      ImageBlob(
       id = None,
       title = "A Figure Bowing before a Seated Old Man with his Arm Outstretched in Benediction. Verso: Indecipherable Sketch",
       description = "Watercolour, ink, chalk and graphite on paper. Verso: graphite on paper",
       date = "date not known",
       author = Some(Person(None, name = "Robert Blake")),
       external_ids = Map("tate:id" -> "1035"))))
    }
}
