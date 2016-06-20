package io.mediachain.transactor

import java.io.File
import io.mediachain.util.Properties
import io.mediachain.copycat.Transport.SSLConfig
import io.mediachain.datastore.DynamoDatastore.{Config => DynamoConfig}

sealed trait RunMode
case object TransactorMode extends RunMode
case object FacadeMode extends RunMode


case class Config(
  mode: RunMode = null,
  conf: Properties = null,
  cluster: List[String] = Nil
)


object Config {
  import scopt._

  val parser = new OptionParser[Config]("mediachain") {
    head("mediachain")
    
    opt[String]('c', "config")
      .required()
      .text("configuration file")
      .action { (file, c) =>
        c.copy(conf = Properties.load(file))
    }

    arg[String]("server-address ...")
      .text("transactor server address")
      .unbounded()
      .required()
      .action { (addr, c) =>
        c.copy(
          cluster = c.cluster ++ List(addr)
        )
      }
    
    cmd("transactor")
      .text("run as transactor server")
      .action { (_, c) =>
        c.copy(mode = TransactorMode)
      }
    
    cmd("facade")
      .text("run as RPC facade to the transactor cluster")
      .action { (_, c) =>
        c.copy(mode = FacadeMode)
      }
  }
}
