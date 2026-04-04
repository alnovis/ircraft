package io.alnovis.ircraft.io

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import java.nio.file.{ Files, Path, StandardCopyOption }
import java.security.MessageDigest

/** Writes only files whose content has changed (based on SHA-256 hash). */
trait IncrementalWriter[F[_]] {
  def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult]
}

case class WriteResult(written: Int, skipped: Int, total: Int)

object IncrementalWriter {

  def apply[F[_]: Async]: IncrementalWriter[F] = new IncrementalWriter[F] {
    def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult] =
      Async[F].interruptible(Files.createDirectories(cacheDir)) *>
        files.toVector
          .parTraverse {
            case (relativePath, content) =>
              writeIfChanged(outputDir, cacheDir, relativePath, content)
          }
          .map { results =>
            val written = results.count(identity)
            WriteResult(written, results.size - written, results.size)
          }
  }

  private def writeIfChanged[F[_]: Async](
    outputDir: Path,
    cacheDir: Path,
    relativePath: Path,
    content: String
  ): F[Boolean] =
    for {
      hash     <- Async[F].delay(sha256(content))
      pathHash <- Async[F].delay(sha256(relativePath.toString))
      hashFile = cacheDir.resolve(pathHash + ".sha256")
      cached <- Async[F].interruptible(
        if (Files.exists(hashFile)) Files.readString(hashFile).trim else ""
      )
      changed <-
        if (hash == cached) false.pure[F]
        else atomicWriteWithCache(outputDir, relativePath, content, hashFile, hash).as(true)
    } yield changed

  /** Write file atomically + update hash cache. */
  private def atomicWriteWithCache[F[_]: Async](
    outputDir: Path,
    relativePath: Path,
    content: String,
    hashFile: Path,
    hash: String
  ): F[Unit] = {
    val target = outputDir.resolve(relativePath)
    val acquire = Async[F].interruptible {
      Files.createDirectories(target.getParent)
      Files.createTempFile(target.getParent, ".ircraft-", ".tmp")
    }
    val release: Path => F[Unit] = tmp => Async[F].interruptible(Files.deleteIfExists(tmp)).void

    Resource.make(acquire)(release).use { tmp =>
      Async[F].interruptible(Files.writeString(tmp, content)) *>
        Async[F].interruptible(
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        ) *>
        Async[F].interruptible(Files.writeString(hashFile, hash)).void
    }
  }

  private def sha256(s: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
