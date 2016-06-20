package io.mediachain.util

import java.util.{Properties => JProperties}
import java.io.FileInputStream

class Properties(jprops: JProperties = new JProperties) {
  def getq(key: String): String =
    getopt(key).getOrElse {throw new RuntimeException("Missing configuration property: " + key)}

  def getopt(key: String): Option[String] =
    Option(get(key))
  
  def get(key: String): String =
    jprops.getProperty(key)
  
  def put(key: String, value: String) {
    jprops.setProperty(key, value)
  }
}

object Properties {
  def load(path: String) = {
    val jprops = new JProperties
    jprops.load(new FileInputStream(path))
    new Properties(jprops)
  }
}
