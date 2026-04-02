package io.alnovis.ircraft.io

import cats.effect.*
import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Writes only files whose content has changed (based on SHA-256 hash). */
trait IncrementalWriter[F[_]]:
  def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult]

case class WriteResult(written: Int, skipped: Int, total: Int)

object IncrementalWriter:

  def apply[F[_]: Sync]: IncrementalWriter[F] = new IncrementalWriter[F]:
    def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult] =
      Sync[F].delay {
        Files.createDirectories(cacheDir)
        var written = 0
        var skipped = 0

        files.foreach { (relativePath, content) =>
          val hash = sha256(content)
          val hashFile = cacheDir.resolve(relativePath.toString.replace('/', '_') + ".sha256")
          val cached = if Files.exists(hashFile) then Files.readString(hashFile).trim else ""

          if hash != cached then
            val target = outputDir.resolve(relativePath)
            Files.createDirectories(target.getParent)
            Files.writeString(target, content)
            Files.writeString(hashFile, hash)
            written += 1
          else
            skipped += 1
        }

        WriteResult(written, skipped, files.size)
      }

  private def sha256(s: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
