package io.mediachain.util.orient

/**
  * This file is lifted from:
  * https://github.com/springnz/orientdb-migrations/blob/master/src/main/scala/springnz/orientdb/ODBScala.scala
  */

import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.metadata.schema.OImmutableClass
import java.time.OffsetDateTime
import java.util.Date

import com.orientechnologies.orient.core.command.OCommandRequest
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

object ODBScala extends ODBScala

trait ODBScala {

  implicit class dbWrapper(db: ODatabaseDocumentTx) {

    def q(sql: String, params: AnyRef*): immutable.IndexedSeq[ODocument] = {
      val params4java = params.toArray
      val results: java.util.List[ODocument] = db.query(new OSQLSynchQuery[ODocument](sql), params4java: _*)
      results.asScala.toIndexedSeq
    }

    def qSingleResult(sql: String, params: AnyRef*): Option[ODocument] = {
      val result = q(sql, params: _*)
      if (result.size > 1)
        throw new RuntimeException("Query returned multiple results, but we only expected one.")
      result.headOption
    }

    def qSingleResultAsInts(sql: String, params: AnyRef*): Option[Seq[Int]] =
      qSingleResult(sql, params: _*).map(_.fieldValues().map(_.asInstanceOf[Int]))

    def count(sql: String, params: AnyRef*): Long =
      qSingleResult(sql, params: _*).get.getLong("count")
  }

  implicit class sqlSynchQueryWrapper[T](sqlSynchQuery: OSQLSynchQuery[T]) {

    def exec(params: AnyRef*)(implicit db: ODatabaseDocumentTx): immutable.IndexedSeq[T] = {
      val results: java.util.List[T] = db.command(sqlSynchQuery).execute(params.toArray: _*)
      results.asScala.toIndexedSeq
    }
  }

  def getSchema(implicit db: ODatabaseDocumentTx) =
    db.getMetadata.getSchema

  def findClass(className: String)(implicit db: ODatabaseDocumentTx) =
    getSchema.getClass(className)

  def createClass(className: String)(implicit db: ODatabaseDocumentTx) =
    getSchema.createClass(className)

  def createClass(className: String, parent: OClass)(implicit db: ODatabaseDocumentTx) = {
    getSchema.createClass(className, parent)
  }

  def createVertexClass(className: String)(implicit db: ODatabaseDocumentTx) = {
    val V = Option(findClass(OImmutableClass.VERTEX_CLASS_NAME)).getOrElse {
      // like in TP2 OrientBaseGraph
      getSchema.createClass(OImmutableClass.VERTEX_CLASS_NAME).setOverSize(2)
    }
    // vertex classes are prefixed with V_ in the orient driver
    createClass(OImmutableClass.VERTEX_CLASS_NAME + "_" + className, V)
  }

  def createEdgeClass(className: String)(implicit db: ODatabaseDocumentTx) = {
    val E = Option(findClass(OImmutableClass.EDGE_CLASS_NAME)).getOrElse {
      // like in TP2 OrientBaseGraph
      getSchema.createClass(OImmutableClass.EDGE_CLASS_NAME)
    }
    // edge classes are prefixed with E_ in the orient driver
    createClass(OImmutableClass.EDGE_CLASS_NAME + "_" + className, E)
  }

  def dropClass(className: String)(implicit db: ODatabaseDocumentTx) =
    getSchema.dropClass(className)

  def truncateClass(className: String)(implicit db: ODatabaseDocumentTx) =
    findClass(className).truncate()

  def sqlCommand(sql: String)(implicit db: ODatabaseDocumentTx): OCommandRequest =
    db.command(new OCommandSQL(sql))

  def escapeSqlString(string: String) = string.replace("\\", "\\\\").replace("\"", "\\\"")

  def selectClass[T](className: String)(mapper: ODocument ⇒ T)(implicit db: ODatabaseDocumentTx): IndexedSeq[T] =
    db.q(s"select from $className").map(mapper)

  def selectClassDocuments(className: String)(implicit db: ODatabaseDocumentTx): IndexedSeq[ODocument] =
    selectClass(className)(identity)

  def dbFuture[T](block: ⇒ T)(implicit db: ODatabaseDocumentTx, ec: ExecutionContext): Future[T] =
    Future {
      ODatabaseRecordThreadLocal.INSTANCE.set(db)
      block
    }

  implicit class docPimper(doc: ODocument) {

    def setDateTime(fieldName: String, dateTime: OffsetDateTime): ODocument =
      doc.field(fieldName, Date.from(dateTime.toInstant))

    def getInt(fieldName: String): Int =
      doc.field(fieldName).asInstanceOf[Int]

    def getLong(fieldName: String): Long =
      doc.field(fieldName).asInstanceOf[Long]

    def getFloat(fieldName: String): Float =
      doc.field(fieldName).asInstanceOf[Float]

    def getDouble(fieldName: String): Double =
      doc.field(fieldName).asInstanceOf[Double]

    def getBigDecimal(fieldName: String): BigDecimal =
      doc.field(fieldName).asInstanceOf[BigDecimal]

    def getString(fieldName: String): String =
      doc.field(fieldName).asInstanceOf[String]
  }
}