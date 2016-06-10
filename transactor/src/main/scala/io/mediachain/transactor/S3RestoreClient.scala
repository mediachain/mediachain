package io.mediachain.transactor

import java.util.Properties
import java.io.FileInputStream
import io.mediachain.copycat.{S3BackingStore, S3Restore}

object S3RestoreClient {
  def main(args: Array[String]) {
    if (args.length < 3) {
      println("Expected arguments: config s3key cluster-ip ...")
      System.exit(1)
    }
    
    val (config :: s3key :: cluster) = args.toList
    val props = new Properties
    props.load(new FileInputStream(config))
    run(props, s3key, cluster)
  }
  
  def run(conf: Properties, s3key: String, cluster: List[String]) {
    val config = S3BackingStore.Config.fromProperties(conf)
    val s3r = new S3Restore(config)
    s3r.restore(cluster, s3key)
  }
}
