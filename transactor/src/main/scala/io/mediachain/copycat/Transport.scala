package io.mediachain.copycat

import io.mediachain.util.Properties
import io.atomix.catalyst.transport.{Transport => CopycatTransport}
import io.atomix.catalyst.transport.netty.NettyTransport

object Transport {
  case class SSLConfig(
    keyStorePath: String,
    keyStorePassword: String,
    keyStoreKeyPassword: String
  )
  
  object SSLConfig {
    def fromProperties(conf: Properties): Option[SSLConfig] = {
      conf.getopt("io.mediachain.transactor.ssl.enabled").flatMap { enabled =>
        if (enabled == "true") {
          val keyStorePath = conf.getq("io.mediachain.transactor.ssl.keyStorePath")
          // these two theoretically can be null
          val keyStorePasswd = conf.get("io.mediachain.transactor.ssl.keyStorePassword")
          val keyStoreKeyPasswd = conf.get("io.mediachain.transactor.ssl.keyStoreKeyPassword")
          Some(SSLConfig(keyStorePath, keyStorePasswd, keyStoreKeyPasswd))
        } else {
          None
        }
      }
    }
  }
  
  def build(threads: Int, sslConfig: Option[SSLConfig]): CopycatTransport = {
    val builder = NettyTransport.builder().withThreads(threads)
    sslConfig match {
      case Some(SSLConfig(path, pass, keypass)) =>
        builder.withSsl()
          .withKeyStorePath(path)
          .withKeyStorePassword(pass)
          .withKeyStoreKeyPassword(keypass)
          .build()
      case None =>
        builder.build()
    }
  }
}
