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
          url <- getEnv("ORIENTDB_URL")
          user <- getEnv("ORIENTDB_USER")
          pass <- getEnv("ORIENTDB_PASS")
        } yield ODBConnectConfig(url, user, pass)
    }


  def getEnv(key: String): Try[String] =
    Try(sys.env.getOrElse(key,
      throw new RuntimeException(s"$key environment var must be defined")))


  def applyToPersistentDB(config: Option[ODBConnectConfig] = None): Try[Unit] = {
    val pool = config.map(poolWithConfig)
      .getOrElse(poolWithConfigFromEnv)

    val migrations = new LSpaceMigrations().migrations
    Migrator.runMigration(migrations)(pool)
  }
}
