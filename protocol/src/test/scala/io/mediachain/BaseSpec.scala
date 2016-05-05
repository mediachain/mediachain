package io.mediachain

import org.specs2.Specification
import org.specs2.matcher.ThrownExpectations

trait BaseSpec extends Specification
  with XorMatchers
  with ThrownExpectations
