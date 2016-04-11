package io.mediachain.util.orient

import com.orientechnologies.orient.core.db.{ODatabaseFactory, ODatabaseRecordThreadLocal}
import io.mediachain.util.Env
import org.apache.commons.configuration.{BaseConfiguration, Configuration}
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import org.apache.tinkerpop.gremlin.structure.Graph
import springnz.orientdb.migration.Migrator
import springnz.orientdb.pool.{ODBConnectConfig, ODBConnectionPool}

import scala.util.{Failure, Random, Success, Try}

object MigrationHelper {
  if (ODatabaseRecordThreadLocal.INSTANCE == null) {
    sys.error("Calling this manually apparently prevents an initialization issue.")
  }

  val DEFAULT_POOL_MAX = Runtime.getRuntime.availableProcessors()

  object EnvVars {
    val ORIENTDB_URL = "ORIENTDB_URL"
    val ORIENTDB_USER = "ORIENTDB_USER"
    val ORIENTDB_PASSWORD = "ORIENTDB_PASSWORD"
    val ORIENTDB_POOL_MAX = "ORIENTDB_POOL_MAX"
  }

  def newInMemoryGraph(transactional: Boolean = true): OrientGraph = {
    getMigratedGraph(Some(inMemoryODBConfig), transactional) match {
      case Failure(e) =>
        throw new IllegalStateException(
          s"Unable to apply migrations to in-memory db: ${e.getMessage}", e)

      case Success(graph) => graph
    }
  }


  def newInMemoryGraphFactory(): OrientGraphFactory =
    getMigratedGraphFactory(Some(inMemoryODBConfig)) match {
      case Failure(e) =>
        throw new IllegalStateException(
          s"Unable to apply migrations to in-memory graph factory", e)

      case Success(factory) => factory
    }


  def inMemoryODBConfig: ODBConnectConfig =
    ODBConnectConfig(s"memory:in-memory-${Random.nextInt}", "admin", "admin")


  def poolWithConfig(config: ODBConnectConfig): ODBConnectionPool =
    new ODBConnectionPool {
      override def dbConfig: Try[ODBConnectConfig] = Success(config)
    }

  def configFromEnv: Try[ODBConnectConfig] =
    for {
      url <- Env.getString(EnvVars.ORIENTDB_URL)
      user <- Env.getString(EnvVars.ORIENTDB_USER)
      pass <- Env.getString(EnvVars.ORIENTDB_PASSWORD)
    } yield ODBConnectConfig(url, user, pass)


  def poolWithConfigFromEnv: ODBConnectionPool =
    new ODBConnectionPool {
      override def dbConfig: Try[ODBConnectConfig] =
        configFromEnv
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



  def graphFactoryWithOptionalConfig(
    configOpt: Option[ODBConnectConfig] = None,
    poolMaxOpt: Option[Int] = None): Try[OrientGraphFactory] = {
    val config = configOpt.orElse(configFromEnv.toOption)

    val poolMax =
      poolMaxOpt
      .getOrElse(Env.getInt(EnvVars.ORIENTDB_POOL_MAX)
        .getOrElse(DEFAULT_POOL_MAX))

    config match {
      case Some(ODBConnectConfig(url, user, pass)) =>
        Try({
          val factoryConfig = new BaseConfiguration {
            setProperty(Graph.GRAPH, classOf[OrientGraph].getName)
            setProperty(OrientGraph.CONFIG_URL, url)
            setProperty(OrientGraph.CONFIG_USER, user)
            setProperty(OrientGraph.CONFIG_PASS, pass)
            setProperty(OrientGraph.CONFIG_LABEL_AS_CLASSNAME, false)
          }
          val factory = new OrientGraphFactory(factoryConfig)
          factory.setupPool(poolMax)
          factory
        })

      case None =>
        Failure(new IllegalStateException(
          "Unable to get graph instance.  Either pass in a configuration, "+
            "or set the required environment variables " +
            s"${EnvVars.ORIENTDB_URL}, ${EnvVars.ORIENTDB_USER}, and " +
            s"${EnvVars.ORIENTDB_PASSWORD}"))
    }
  }

  def graphWithOptionalConfig(
    configOpt: Option[ODBConnectConfig],
    transactional: Boolean = true): Try[OrientGraph] = {
    graphFactoryWithOptionalConfig(configOpt)
      .map { factory =>
        if (transactional) factory.getTx()
        else factory.getNoTx()
      }
  }

  def getMigratedGraph(
    config: Option[ODBConnectConfig] = None,
    transactional: Boolean = true
  ): Try[OrientGraph] =
    for {
      _ <- applyToPersistentDB(config)
      graph <- graphWithOptionalConfig(config, transactional)
    } yield graph


  def getMigratedGraphFactory(
    configOpt: Option[ODBConnectConfig] = None,
    poolMaxOpt: Option[Int] = None
  ): Try[OrientGraphFactory] =
    for {
      _ <- applyToPersistentDB(configOpt)
      factory <- graphFactoryWithOptionalConfig(configOpt, poolMaxOpt)
    } yield factory

}
