package io.mediachain

import java.util.logging.Level

import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.exception.OValidationException
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import io.mediachain.Types._
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.specs2.Specification
import gremlin.scala._
import org.specs2.matcher.ThrownExpectations
import org.specs2.specification.{AfterAll, BeforeAll}

object OrientSchemaSpec extends Specification
  with Orientable
  with ThrownExpectations
  with BeforeAll
  with AfterAll
{
  def is =
  s2"""
    - Enforces uniqueness of canonicalID $enforcesUniqueness
    - Enforces existence of mandatory props $enforcesMandatory
    - Enforces readOnly constraint $enforcesReadOnly
    """


  // With the log level set to `INFO`, caught exceptions get
  // printed to the console.  This sets the level to WARNING
  // for the duration of the test.
  def beforeAll: Unit = {
    OLogManager.instance.setConsoleLevel(Level.WARNING.getName)
  }

  // It would be nice to capture the current log level in the
  // beforeAll hook, but OLogManager doesn't expose it :(
  def afterAll: Unit = {
    OLogManager.instance.setConsoleLevel(Level.INFO.getName)
  }


  def enforcesUniqueness = { graph: OrientGraph =>
    val c = Canonical.create()
    graph + c
    (graph + c) must throwA[ORecordDuplicatedException]
  }


  def enforcesMandatory = { graph: OrientGraph =>
    // Person.name is mandatory, so trying to create without
    // that prop should throw
    (graph + "Person") must throwA[OValidationException]
  }


  def enforcesReadOnly = { graph: OrientGraph =>
    val photoV = graph + ImageBlob(None, "title", "desc", "date", None)

    photoV.setProperty(ImageBlob.Keys.title, "new Title") must throwA[OValidationException]
  }
}
