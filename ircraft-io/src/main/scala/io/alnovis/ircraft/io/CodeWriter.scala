package io.alnovis.ircraft.io

import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import java.nio.file.{Files, Path, StandardCopyOption}

/** Writes generated files to disk. */
trait CodeWriter[F[_]]:
  def write(outputDir: Path, files: Map[Path, String]): F[Int]

object CodeWriter:

  def apply[F[_]: Async]: CodeWriter[F] = new CodeWriter[F]:
    def write(outputDir: Path, files: Map[Path, String]): F[Int] =
      files.toVector.parTraverse_(atomicWrite(outputDir)).as(files.size)

  /** Write a single file atomically: temp -> content -> rename. Cleanup on any error via Resource. */
  private def atomicWrite[F[_]: Async](outputDir: Path)(relativePath: Path, content: String): F[Unit] =
    val target = outputDir.resolve(relativePath)
    val acquire = Async[F].interruptible {
      Files.createDirectories(target.getParent)
      Files.createTempFile(target.getParent, ".ircraft-", ".tmp")
    }
    val release: Path => F[Unit] = tmp =>
      Async[F].interruptible(Files.deleteIfExists(tmp)).void

    Resource.make(acquire)(release).use { tmp =>
      Async[F].interruptible(Files.writeString(tmp, content)) *>
        Async[F].interruptible(Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)).void
    }
