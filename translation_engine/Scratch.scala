package org.mediachain.translation

import java.io.File

import cats.data.Xor
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import org.mediachain.Ingress
import org.mediachain.Types._
import gremlin.scala._
import org.mediachain.translation._
import org.mediachain.translation.tate.TateTranslator.TateArtworkContext


import scala.io.Source

object Foo {
  def ingestDirectory(graph: Graph): Unit = {
    val context = TateArtworkContext("directory ingestion test")
    val fixtureDir = new File("/Users/yusef/Work/Code/L-SPACE/translation_engine/test-resources/datasets/tate")
    val files = DirectoryWalker.findWithExtension(fixtureDir, ".json")
    val jsonStrings = files.map(Source.fromFile(_).mkString)
    val translated: Vector[Xor[TranslationError, (PhotoBlob, RawMetadataBlob)]] =
      jsonStrings.map(context.translate)

    translated.foreach {
      resultXor: Xor[TranslationError, (PhotoBlob, RawMetadataBlob)] =>
        resultXor.flatMap { result: (PhotoBlob, RawMetadataBlob) =>
          Ingress.addPhotoBlob(graph, result._1, Some(result._2))
        }
    }

  }

  val graph = new OrientGraphFactory("memory:repl").getNoTx()
  val vertexStepLabel = StepLabel[Vertex]("blobStep")
}

