package io.mediachain.transactor

import java.io.{File, FileInputStream}
import java.util.Properties

import io.mediachain.copycat.Transport.SSLConfig
import io.mediachain.datastore.DynamoDatastore.{Config => DynamoConfig}


case class Endpoint(host: String, port: Int) {
  def asString = s"$host:$port"
}

object Endpoint {
  def apply(s: String): Endpoint = {
    val parts = s.split(":")
    Endpoint(parts(0), parts(1).toInt)
  }
}

sealed trait RunMode
case object TransactorMode extends RunMode
case object FacadeMode extends RunMode


case class Config(
  mode: RunMode = null,
  interactive: Boolean = false,
  listenAddress: Endpoint = null,
  clusterAddresses: Seq[Endpoint] = Seq(),
  transactorDataDir: File = null,
  sslConfig: Option[SSLConfig] = None,
  dynamoConfig: DynamoConfig = DynamoConfig("Mediachain")
)


object Config {
  import scopt._

  val parser = new OptionParser[Config]("mediachain") {
    head("mediachain")

    opt[String]('l', "listen")
      .required()
      .text("bind address")
      .valueName("<host:port>")
      .action { (addrString, c) =>
        c.copy(listenAddress = Endpoint(addrString))
      }

    opt[Seq[String]]('c', "cluster-addresses")
      .text("addresses of transactor cluster members")
      .valueName("<host1:port1>,<host2:port2>")
      .action { (addrStrings, c) =>
        c.copy(
          clusterAddresses = addrStrings.map(Endpoint.apply)
        )
      }

    opt[String]('t', "table-name")
      .text("base name of dynamo-db table")
      .action { (name, c) =>
        c.copy(
          dynamoConfig = c.dynamoConfig.copy(baseTable = name)
        )
      }

    opt[File]('s', "ssl-properties")
      .text("path to properties file for ssl configuration")
      .action { (propsFile, c) =>
        val props = new Properties
        props.load(new FileInputStream(propsFile))
        val sslConfig = SSLConfig.fromProperties(props).getOrElse(
          throw new IllegalArgumentException("Unable to parse ssl config file")
        )
        c.copy(sslConfig = Some(sslConfig))
      }

    opt[String]('e', "dynamo-endpoint")
      .text("endpoint for dynamo-db")
      .action { (endpointString, c) =>
        val httpString =
          if (endpointString.startsWith("http://")) endpointString
          else s"http://${Endpoint(endpointString).asString}"

        c.copy(
          dynamoConfig = c.dynamoConfig.copy(endpoint = Some(httpString))
        )
      }

    cmd("transactor").action { (_, c) =>
      c.copy(mode = TransactorMode)
    }.text("run the transactor")
      .children(
        opt[File]('d', "data")
          .text("directory to store transactor data")
          .required()
          .action { (dir, c) =>
            c.copy(
              transactorDataDir = dir
            )
          },

        opt[Unit]('i', "interactive")
          .text("accept control commands from standard input")
          .action { (_, c) =>
            c.copy(interactive = true)
          }
      )

    cmd("facade")
      .text("run an RPC facade to the transactor cluster")
      .action { (_, c) =>
        c.copy(mode = FacadeMode)
      }
  }
}
