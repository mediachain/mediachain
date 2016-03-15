package io.mediachain.translation

import java.io.File

object SpecResources {
  def resourceUrl(path: String) = this.getClass.getResource(path)

  lazy val simpleTestResourceUrl = resourceUrl("/simple-test.json")

  object Tate {
    lazy val fixtureDir = new File(resourceUrl("/datasets/tate").toURI)

    def sampleDataExists = {
      fixtureDir.exists && fixtureDir.isDirectory
    }

    object SampleArtworkA00001 {
      val jsonFile = new File(fixtureDir, "/artworks/a/000/a00001-1035.json")
      val title = "A Figure Bowing before a Seated Old Man with his Arm Outstretched in Benediction. Verso: Indecipherable Sketch"
      val medium = "Watercolour, ink, chalk and graphite on paper. Verso: graphite on paper"
      val dateText = "date not known"
      val artistName = "Robert Blake"
    }
  }
}
