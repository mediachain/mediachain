package org.mediachain.io

import com.orientechnologies.orient.core.id.ORecordId
import org.mediachain.XorMatchers
import org.specs2.Specification

object HashingSpec extends Specification with XorMatchers {
  import org.mediachain.Types._

  def is =
    s2"""
         $hashesCanonical - Hashes a Canonical, ignoring the 'id' field
         $differentDataDifferentHash - Non-identical data produces non-identical hash
      """

  def hashesCanonical = {
    val canonical = Canonical(None, "foobar")
    val canonicalWithId = Canonical(Some(new ORecordId("#0:1")), "foobar")

    canonical.multiHash must beRightXor { withoutId: MultiHash =>
      canonicalWithId.multiHash must beRightXor { withId: MultiHash =>
        withId must_== withoutId
      }
    }
  }


  def differentDataDifferentHash = {
    val blob1 = PhotoBlob(None,
      "Dogs playing backgammon",
      "Awww, they think they're people!",
      "March 15th 2016",
      None)

    val blob2 = blob1.copy(date = "March 15th, 2016")

    blob1.multiHash must beRightXor { b1: MultiHash =>
      blob2.multiHash must beRightXor { b2: MultiHash =>
        b1 must_!= b2
      }
    }
  }
}
