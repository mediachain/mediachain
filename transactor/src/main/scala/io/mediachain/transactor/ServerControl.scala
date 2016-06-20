package io.mediachain.transactor

import java.nio.file.{Files, FileSystems, Path, Paths}
import java.nio.file.StandardWatchEventKinds._
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConversions._
import sys.process._

class ServerControl(
  ctlpath: String,
  commands: Map[String, (String) => Unit]
) extends Runnable {
  val logger = LoggerFactory.getLogger(classOf[ServerControl])
  val dir = Paths.get(ctlpath)
  val watcher = FileSystems.getDefault.newWatchService()
  // touch a non-existent file will emit both create and modify, so we just track modify
  dir.register(watcher, ENTRY_MODIFY)
  
  def run {
    while (true) {
      val key = watcher.take()
      for (event <- key.pollEvents) {
        try {
          doCommand(event.context.asInstanceOf[Path])
        } catch {
          case e: Throwable => logger.error("Error processing control command", e)
        }
      }
      if (!key.reset()) {
        logger.error(s"Control directory ${ctlpath} seems to be inaccesible!?")
      }
    }
  }
  
  def doCommand(file: Path) {
    val cmd = file.toString
    logger.info(s"Control command: ${cmd}")
    commands.get(cmd) match {
      case Some(fun) => fun(cmd)
      case None => logger.error(s"Unknown command ${cmd}")
    }
  }
}

object ServerControl {
  def build(ctlpath: String, commands: Map[String, (String) => Unit]) = {
    (s"mkdir -p ${ctlpath}").!
    commands.keys.foreach { cmd => (s"touch ${ctlpath}/${cmd}").! }
    new ServerControl(ctlpath, commands)
  }
}
