package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.ODatabaseFactory
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraphFactory, OrientGraph}
import springnz.orientdb.migration.Migrator
import springnz.orientdb.pool.{ODBConnectionPool, ODBConnectConfig}

import scala.util.{Failure, Success, Try, Random}

object MigrationHelper {

  def newInMemoryGraph(transactional: Boolean = true): OrientGraph = {
    val dbname = s"memory:in-memory-${Random.nextInt()}"
    // create the db, but don't open it yet
    val rawDb = new ODatabaseFactory().createDatabase("graph", dbname)

    // apply the migrations
    val config = ODBConnectConfig(dbname, "admin", "admin")
    val pool = new ODBConnectionPool {
      override def dbConfig: Try[ODBConnectConfig] = Success(config)
    }

    val migrations = new LSpaceMigrations().migrations

    Migrator.runMigration(migrations)(pool) match {
      case Failure(e) =>
        throw new IllegalStateException(
          s"Unable to apply migrations to in-memory db: ${e.getMessage}"
        )
      case _ => ()
    }

    // return a new graph using the migrated db
    if (transactional) {
      new OrientGraphFactory(dbname).getTx(false, true)
    } else {
      new OrientGraphFactory(dbname).getNoTx(false, true)
    }
  }

  def applyToDBConfigAtPath(configFilePath: String): Try[Unit] = {
    val migrations = new LSpaceMigrations().migrations

    val pool = ODBConnectionPool.fromConfig(configFilePath)
    Migrator.runMigration(migrations)(pool)
  }
}
