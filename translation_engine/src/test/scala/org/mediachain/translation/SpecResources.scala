package org.mediachain.translation

object SpecResources {
  def resourceUrl(path: String) = this.getClass.getResource(path)

  lazy val simpleTestResourceUrl = resourceUrl("/simple-test.json")
  lazy val tateSampleDirResourceUrl = resourceUrl("/tate-sample")
  val numTateSamples = 18
}
