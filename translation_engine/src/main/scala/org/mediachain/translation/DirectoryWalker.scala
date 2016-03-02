package org.mediachain.translation

object DirectoryWalker {

  import java.io.File

  def walkTree(file: File): Iterable[File] = {
    val children = new Iterable[File] {
      def iterator = if (file.isDirectory) file.listFiles.iterator else Iterator.empty
    }
    Seq(file) ++: children.flatMap(walkTree(_))
  }

  def findWithExtension(dir: File, ext: String): Iterable[File] = {
    for (f <- walkTree(dir) if f.getName.endsWith(ext)) yield f
  }
}
