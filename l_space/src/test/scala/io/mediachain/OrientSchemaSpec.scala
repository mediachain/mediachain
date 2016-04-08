package io.mediachain

import java.util.logging.Level

import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.exception.OValidationException
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import io.mediachain.Types._
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import gremlin.scala._
import io.mediachain.core.GraphError.TransactionFailed
import org.specs2.specification.{AfterAll, BeforeAll}
import io.mediachain.util.GremlinUtils.withTransaction

object OrientSchemaSpec extends BaseSpec
  with Orientable
  with BeforeAll
  with AfterAll
{
  def is =
  s2"""
    - Enforces uniqueness of canonicalID $enforcesUniqueness
    - Enforces existence of mandatory props $enforcesMandatory
    - Enforces readOnly constraint $enforcesReadOnly
    - Enforces unique edges $enforcesUniqueEdges
    - Enforces unique edges outside a transaction $enforcesUniqueEdgesOutsideTx
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
    val photoV = graph + ImageBlob(None, "title", "desc", "date")

    photoV.setProperty(ImageBlob.Keys.title, "new Title") must throwA[OValidationException]
  }

  def enforcesUniqueEdges = { graph: OrientGraph =>
    withTransaction(graph) {
      val imageV = graph + ImageBlob(None, "foo", "bar", "baz")
      val rawV = graph + RawMetadataBlob(None, "blob")
      imageV --- TranslatedFrom --> rawV
      imageV --- TranslatedFrom --> rawV
    } must beLeftXor { err =>
      err must beLikeA {
        case TransactionFailed(ex) =>
          ex must beAnInstanceOf[ORecordDuplicatedException]
      }
    }
  }


  // Unfortunately, this is currently broken (as of March 8, 2016)
  // No exception is thrown, and both edges are added to the graph.
  // It's not yet clear whether this is a bug in orientdb itself,
  // or if the orientdb-gremlin driver we're depending on for
  // gremlin 3.0 support may be responsible.
  //
  // For now, make sure you create edges inside a transaction!
  def enforcesUniqueEdgesOutsideTx = pending { graph: OrientGraph =>
    def doTheThing() = {
      val imageV = graph + ImageBlob(None, "foo", "bar", "baz")
      val rawV = graph + RawMetadataBlob(None, "blob")
      imageV --- TranslatedFrom --> rawV
      imageV --- TranslatedFrom --> rawV
    }

    doTheThing must throwAn[ORecordDuplicatedException]
  }
}
