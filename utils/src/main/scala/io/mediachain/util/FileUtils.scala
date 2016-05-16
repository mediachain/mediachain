package io.mediachain.util



object FileUtils {
  import java.io.File
  import cats.data.Xor
  import cats.std.list._
  import cats.syntax.foldable._

  case class FileDeletionFailed(path: String)

  def delete(file: File): Xor[FileDeletionFailed, Unit] =
    if (file.delete()) Xor.right({})
    else Xor.left(FileDeletionFailed(file.getAbsolutePath))

  def delete(path: String): Xor[FileDeletionFailed, Unit] = delete(new File(path))

  def rm_rf(file: File): Xor[FileDeletionFailed, Unit] = {
    val files = Option(file.listFiles).toList.flatMap(_.toList) ++ List(file)
    files.traverseU_(delete)
  }

  def rm_rf(path: String): Xor[FileDeletionFailed, Unit] = rm_rf(new File(path))
}
