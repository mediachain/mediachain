package io.mediachain.copycat

import io.atomix.catalyst.transport.{Transport => CopycatTransport}
import io.atomix.catalyst.transport.netty.NettyTransport

object Transport {
  case class SSLConfig(
    keyStorePath: String,
    keyStorePassword: String,
    keyStoreKeyPassword: String
  )
  
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
