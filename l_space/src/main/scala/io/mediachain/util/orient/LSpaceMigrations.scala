package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import springnz.orientdb.migration.{Migration, ODBMigrations}
import springnz.orientdb.session.ODBSession
import io.mediachain.Types._

class LSpaceMigrations extends ODBMigrations with OrientSchema {

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

        VertexClass("PhotoBlob",
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

  override def migrations: Seq[Migration] = Seq (
    Migration(0, migration0)
  )
}
