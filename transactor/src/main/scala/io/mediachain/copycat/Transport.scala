package io.mediachain.copycat

import java.util.Properties
import io.atomix.catalyst.transport.{Transport => CopycatTransport}
import io.atomix.catalyst.transport.netty.NettyTransport

object Transport {
  case class SSLConfig(
    keyStorePath: String,
    keyStorePassword: String,
    keyStoreKeyPassword: String
  )
  
  object SSLConfig {
    def fromProperties(props: Properties): Option[SSLConfig] = {
      def getq(key: String): String =
        getopt(key).getOrElse {throw new RuntimeException("Missing configuration property: " + key)}

      def getopt(key: String) =
        Option(get(key))
      
      def get(key: String) = 
        props.getProperty(key)
      
      getopt("io.mediachain.transactor.ssl.enabled").flatMap { enabled =>
        if (enabled == "true") {
          val keyStorePath = getq("io.mediachain.transactor.ssl.keyStorePath")
          // these two theoretically can be null
          val keyStorePasswd = get("io.mediachain.transactor.ssl.keyStorePassword")
          val keyStoreKeyPasswd = get("io.mediachain.transactor.ssl.keyStoreKeyPassword")
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
