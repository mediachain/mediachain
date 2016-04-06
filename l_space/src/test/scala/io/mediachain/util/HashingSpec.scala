package io.mediachain.util

import com.orientechnologies.orient.core.id.ORecordId
import io.mediachain.Types._
import io.mediachain.XorMatchers
import org.specs2.Specification

object HashingSpec extends Specification with XorMatchers {

  def is =
    s2"""
         $hashesCanonical - Hashes a Canonical, ignoring the 'id' field
         $differentDataDifferentHash - Non-identical data produces non-identical hash
      """

  def hashesCanonical = {
    val canonical = Canonical(None, "foobar")
    val canonicalWithId = Canonical(Some(new ORecordId("#0:1")), "foobar")

    canonical.multiHash must_== canonicalWithId.multiHash
  }


  def differentDataDifferentHash = {
    val blob1 = ImageBlob(None,
      "Dogs playing backgammon",
      "Awww, they think they're people!",
      "March 15th 2016")

    val blob2 = blob1.copy(date = "March 15th, 2016")

    blob1.multiHash must_!= blob2.multiHash
  }
}
