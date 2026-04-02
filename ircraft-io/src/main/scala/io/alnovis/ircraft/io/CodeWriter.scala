package io.alnovis.ircraft.io

import cats.effect.*
import java.nio.file.{Files, Path}

/** Writes generated files to disk. */
trait CodeWriter[F[_]]:
  def write(outputDir: Path, files: Map[Path, String]): F[Int]

object CodeWriter:

  def apply[F[_]: Sync]: CodeWriter[F] = new CodeWriter[F]:
    def write(outputDir: Path, files: Map[Path, String]): F[Int] =
      Sync[F].delay {
        files.foreach { (relativePath, content) =>
          val target = outputDir.resolve(relativePath)
          Files.createDirectories(target.getParent)
          Files.writeString(target, content)
        }
        files.size
      }
