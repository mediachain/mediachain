package io.mediachain.util

import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.dto.Point


class Metrics(influx: InfluxDB, db: String) {
  def counter(measure: String, tags: Map[String, String], count: Int = 1) {
    val pt = Point.measurement(measure)
      .tag(tags)
      .addField("count", count)
      .build()
    influx.write(db, "DEFAULT", pt)
  }
}

object Metrics {
  def connect(url: String, user: String, pass: String, db: String): Metrics = {
    val influx = InfluxDBFactory.connect(url, user, pass)
    influx.enableBatch(1000, 1, TimeUnit.SECONDS)
    new Metrics(influx, db)
  }
  
  def fromPropeties(conf: Properties): Option[Metrics] = {
    conf.getopt("io.mediachain.transactor.metrics.enabled").flatMap { enabled =>
      if (enabled == "true") {
        val url = conf.getq("io.mediachain.transactor.metrics.url")
        val user = conf.getq("io.mediachain.transactor.metrics.user")
        val pass = conf.getq("io.mediachain.transactor.metrics.pass")
        val db = conf.getq("io.mediachain.transactor.metrics.db")
        Some(connect(url, user, pass, db))
      } else {
        None
      }
    }
  }
}
