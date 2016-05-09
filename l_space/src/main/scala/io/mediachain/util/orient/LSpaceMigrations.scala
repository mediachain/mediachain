package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OClass
import springnz.orientdb.migration.{Migration, ODBMigrations}
import springnz.orientdb.session.ODBSession
import io.mediachain.Types._

class LSpaceMigrations extends ODBMigrations with OrientSchema {

  // Create initial set of vertex and edge classes
  def migration0: ODBSession[Unit] =
    ODBSession { implicit db: ODatabaseDocumentTx =>

      val multiHashProp =
        StringProperty("multiHash").mandatory(true).readOnly(true).unique(true)

      db ++ Seq (
        VertexClass("Canonical",
          multiHashProp,
          StringProperty("canonicalID")
            .mandatory(true)
            .readOnly(true)
            .unique(true)
        ),

        VertexClass("ImageBlob",
          multiHashProp,
          StringProperty("title").readOnly(true),
          StringProperty("description").readOnly(true),
          StringProperty("date").readOnly(true)
        ),

        VertexClass("Person",
          multiHashProp,
          StringProperty("name").mandatory(true).readOnly(true)
        ),

        VertexClass("RawMetadataBlob",
          multiHashProp,
          StringProperty("blob").mandatory(true).readOnly(true)
        ),

        EdgeClass(DescribedBy),
        EdgeClass(ModifiedBy),
        EdgeClass(TranslatedFrom),
        EdgeClass(AuthoredBy)
      )

      () // explicitly return unit
    }

  // Add "signatures" property as an embedded map
  def migration1: ODBSession[Unit] =
    ODBSession {implicit db: ODatabaseDocumentTx =>

      val signaturesProp = MapProperty("signatures")
        .mandatory(true)
        .defaultValue(Map[String,String]())


      val classNames =
        List("Canonical", "ImageBlob", "Person", "RawMetadataBlob")
        .map("V_" + _)

      val classOpts = classNames.map(db.findClass)
      for {
        classOpt <- classOpts
        klass <- classOpt.toList
      } yield klass + signaturesProp
      ()
    }

  // Make index on keys of the "signatures" property for each vertex class
  def migration2: ODBSession[Unit] =
    ODBSession { implicit db: ODatabaseDocumentTx =>
      val classNames =
        List("Canonical", "ImageBlob", "Person", "RawMetadataBlob")

      classNames.foreach { klass =>
        val sql =
          s"CREATE INDEX ${klass}SignatureCommonNameIndex " +
            s"ON V_$klass (signatures BY KEY) NOTUNIQUE_HASH_INDEX STRING"
        db.executeSqlCommand[Long](sql)
      }
    }

  // add "external_ids" property and index
  def migration3: ODBSession[Unit] =
    ODBSession {implicit db: ODatabaseDocumentTx =>

      val idsProp = MapProperty("external_ids")
        .mandatory(true)
        .defaultValue(Map[String,String]())


      val classNames =
        List("ImageBlob", "Person")
          .map("V_" + _)

      val classOpts = classNames.map(db.findClass)
      for {
        classOpt <- classOpts
        klass <- classOpt.toList
      } yield klass + idsProp

      classNames.foreach { klass =>
        val sql =
          s"CREATE INDEX ${klass}ExternalIdsIndex " +
            s"ON $klass (external_ids BY KEY) NOTUNIQUE_HASH_INDEX STRING"
        db.executeSqlCommand[Long](sql)
      }
      ()
    }

  // add edge constraints
  def migration4: ODBSession[Unit] =
    ODBSession { implicit db: ODatabaseDocumentTx =>

      // AuthoredBy can go from anything to a Canonical, must have only
      // one edge per pair of vertices
      for {
        authoredBy <- db.findEdgeClass(AuthoredBy)
        baseVertexClass <- db.findClass("V")
        canonicalClass <- db.findVertexClass("Canonical")
      } yield db.addEdgeConstraint(authoredBy,
          EdgeConstraint(baseVertexClass, canonicalClass, unique = true))


      // TranslatedFrom can go from anything to a RawMetadataBlob,
      // must have only one edge per pair of vertices
      for {
        translatedFrom <- db.findEdgeClass(TranslatedFrom)
        baseVertexClass <- db.findClass("V")
        rawMetadataClass <- db.findVertexClass("RawMetadataBlob")
      } yield db.addEdgeConstraint(translatedFrom,
          EdgeConstraint(baseVertexClass, rawMetadataClass, unique = true))


      // Described by can go from a Canonical to anything,
      // must have only one edge per pair of vertices
      for {
        describedBy <- db.findEdgeClass(DescribedBy)
        canonicalClass <- db.findVertexClass("Canonical")
        baseVertexClass <- db.findClass("V")
      } yield db.addEdgeConstraint(describedBy,
        EdgeConstraint(canonicalClass, baseVertexClass, unique = true))

    }

  override def migrations: Seq[Migration] = Seq (
    Migration(0, migration0),
    Migration(1, migration1),
    Migration(2, migration2),
    Migration(3, migration3),
    Migration(4, migration4)
  )
}
