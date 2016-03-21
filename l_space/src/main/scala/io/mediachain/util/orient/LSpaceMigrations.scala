package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import springnz.orientdb.migration.{Migration, ODBMigrations}
import springnz.orientdb.session.ODBSession
import io.mediachain.Types._

class LSpaceMigrations extends ODBMigrations with OrientSchema {

  def migration0: ODBSession[Unit] =
    ODBSession { implicit db: ODatabaseDocumentTx =>
    db ++ Seq (
      VertexClass("Canonical",
        StringProperty("canonicalID").mandatory(true).readOnly(true)
      ),

      VertexClass("PhotoBlob",
        StringProperty("title").readOnly(true),
        StringProperty("description").readOnly(true),
        StringProperty("date").readOnly(true)
      ),

      VertexClass("Person",
        StringProperty("name").mandatory(true).readOnly(true)
      ),

      VertexClass("RawMetadataBlob",
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
