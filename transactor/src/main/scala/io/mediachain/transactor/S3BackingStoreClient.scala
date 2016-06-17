package io.mediachain.transactor

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import io.mediachain.copycat.S3BackingStore
import io.mediachain.util.Properties

object S3BackingStoreClient {
  def main(args: Array[String]) {
    if (args.length < 2) {
      println("Expected arguments: config cluster-ip ...")
      System.exit(1)
    }
    
    val config = args.head
    val cluster = args.toList.tail
    val props = Properties.load(config)
    run(props, cluster)
  }
  
  def run(conf: Properties, cluster: List[String]) {
    val config = S3BackingStore.Config.fromProperties(conf)
    val s3bs = new S3BackingStore(config)
    s3bs.connect(cluster)
    Await.result((Promise[Unit]).future, Duration.Inf)
  }
}

