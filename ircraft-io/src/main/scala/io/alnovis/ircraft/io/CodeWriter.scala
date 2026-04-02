package io.alnovis.ircraft.io

import cats.effect.*
import cats.syntax.all.*
import java.nio.file.{Files, Path}

/** Writes generated files to disk. */
trait CodeWriter[F[_]]:
  def write(outputDir: Path, files: Map[Path, String]): F[Int]

object CodeWriter:

  def apply[F[_]: Sync]: CodeWriter[F] = new CodeWriter[F]:
    def write(outputDir: Path, files: Map[Path, String]): F[Int] =
      files.toVector.traverse_ { (relativePath, content) =>
        Sync[F].blocking {
          val target = outputDir.resolve(relativePath)
          Files.createDirectories(target.getParent)
          Files.writeString(target, content)
        }
      }.as(files.size)
