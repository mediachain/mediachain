package io.mediachain.translation

object DirectoryWalker {

  import java.io.File

  def walkTree(file: File): Iterator[File] = {
    val children = if (file.isDirectory) file.listFiles.iterator else Iterator[File]()
    val files = Iterator(file) ++ children.flatMap(walkTree)
    files
  }

  def findWithExtension(dir: File, ext: String): Iterator[File] = {
    val lowerCaseExt = ext.toLowerCase
    walkTree(dir).filter(_.getName.toLowerCase.endsWith(lowerCaseExt))
  }
}
