package org.mediachain.translation

object SpecResources {
  def resourceUrl(path: String) = this.getClass.getResource(path)

  lazy val simpleTestResourceUrl = resourceUrl("/simple-test.json")
  lazy val tateSampleDirResourceUrl = resourceUrl("/tate-sample")
  val numTateSamples = 18

  object TateSampleArtworkA00001 {
    lazy val url = resourceUrl("/tate-sample/artworks/a/000/a00001-1035.json")
    val title = "A Figure Bowing before a Seated Old Man with his Arm Outstretched in Benediction. Verso: Indecipherable Sketch"
    val medium = "Watercolour, ink, chalk and graphite on paper. Verso: graphite on paper"
    val dateText = "date not known"
    val artistName = "Robert Blake"
  }
}
