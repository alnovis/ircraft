package io.alnovis.ircraft.io

import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import java.nio.file.{Files, Path}

/** Writes generated files to disk. */
trait CodeWriter[F[_]]:
  def write(outputDir: Path, files: Map[Path, String]): F[Int]

object CodeWriter:

  def apply[F[_]: Async]: CodeWriter[F] = new CodeWriter[F]:
    def write(outputDir: Path, files: Map[Path, String]): F[Int] =
      files.toVector.parTraverse_ { (relativePath, content) =>
        Async[F].interruptible {
          val target = outputDir.resolve(relativePath)
          Files.createDirectories(target.getParent)
          Files.writeString(target, content)
        }
      }.as(files.size)
