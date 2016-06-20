package io.mediachain.transactor

import io.mediachain.copycat.S3BackingStore
import io.mediachain.util.Properties
import org.slf4j.{Logger, LoggerFactory}

object S3BackingStoreClient {
  val logger = LoggerFactory.getLogger("io.mediachain.transactor.S3BackingStoreClient")
  
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
    val ctldir = conf.getq("io.mediachain.transactor.S3Backup.control")
    val config = S3BackingStore.Config.fromProperties(conf)
    val s3bs = new S3BackingStore(config)
    s3bs.connect(cluster)
    serverControlLoop(ctldir, s3bs)
  }
  
  def serverControlLoop(ctldir: String, s3bs: S3BackingStore) {
    def shutdown(what: String) {
      logger.info("shutting down")
      s3bs.close()
      System.exit(0)
    }
    
    val ctl = ServerControl.build(ctldir, Map("shutdown" -> shutdown _))
    ctl.run
  }
}

