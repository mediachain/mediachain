package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.ODatabaseFactory
import io.mediachain.util.Env
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import springnz.orientdb.migration.Migrator
import springnz.orientdb.pool.{ODBConnectConfig, ODBConnectionPool}

import scala.util.{Failure, Random, Success, Try}

object MigrationHelper {

  def newInMemoryGraph(transactional: Boolean = true): OrientGraph = {
    val dbname = s"memory:in-memory-${Random.nextInt()}"
    // create the db, but don't open it yet
    val rawDb = new ODatabaseFactory().createDatabase("graph", dbname)

    // apply the migrations
    val config = ODBConnectConfig(dbname, "admin", "admin")
    val pool = poolWithConfig(config)

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


  def poolWithConfig(config: ODBConnectConfig): ODBConnectionPool =
    new ODBConnectionPool {
      override def dbConfig: Try[ODBConnectConfig] = Success(config)
    }


  def poolWithConfigFromEnv: ODBConnectionPool =
    new ODBConnectionPool {
      override def dbConfig: Try[ODBConnectConfig] =
        for {
          url <- Env.getString("ORIENTDB_URL")
          user <- Env.getString("ORIENTDB_USER")
          pass <- Env.getString("ORIENTDB_PASSWORD")
        } yield ODBConnectConfig(url, user, pass)
    }


  def applyToPersistentDB(config: Option[ODBConnectConfig] = None): Try[Unit] = {
    val pool = config.map(poolWithConfig)
      .getOrElse(poolWithConfigFromEnv)

    val migrations = new LSpaceMigrations().migrations
    Migrator.runMigration(migrations)(pool)
  }


  def applyToPersistentDB(url: String, user: String, password: String)
  : Try[Unit] =
    applyToPersistentDB(Some(ODBConnectConfig(url, user, password)))
}
