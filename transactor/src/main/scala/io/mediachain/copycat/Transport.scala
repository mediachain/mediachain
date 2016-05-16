package io.mediachain.copycat

import io.atomix.catalyst.transport.{Transport => CopycatTransport, NettyTransport}

object Transport {
  def build(threads: Int, keyStorePath: Option[String]): CopycatTransport = {
    val builder = NettyTransport.builder().withThreads(threads)
    keyStorePath match {
      case Some(path) =>
        builder.withKeyStorePath(path).build()
      case None =>
        builder.build()
    }
  }
}
