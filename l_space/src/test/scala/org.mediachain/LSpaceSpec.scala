package org.mediachain
import org.specs2._

/**
  * Created by arkadiy on 2/19/16.
  */
object LSpaceSpec extends Specification { def is=s2"""
  A test class where Hello World must have 11 chars $e1
  """

  def e1 = "Hello world" must have size(11)
}
