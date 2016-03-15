package org.mediachain

import com.orientechnologies.orient.core.id.ORecordId
import org.mediachain.io.MultiHash
import org.specs2.Specification

object HashingSpec extends Specification with XorMatchers {
  import org.mediachain.Types._

  def is =
    s2"""
         $hashesCanonical - Hashes a Canonical, ignoring the 'id' field
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

}
