package io.alnovis.ircraft.io

import cats.effect.*
import cats.syntax.all.*
import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Writes only files whose content has changed (based on SHA-256 hash). */
trait IncrementalWriter[F[_]]:
  def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult]

case class WriteResult(written: Int, skipped: Int, total: Int)

object IncrementalWriter:

  def apply[F[_]: Sync]: IncrementalWriter[F] = new IncrementalWriter[F]:
    def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult] =
      Sync[F].blocking(Files.createDirectories(cacheDir)) *>
        files.toVector.foldLeftM(WriteResult(0, 0, files.size)) { case (acc, (relativePath, content)) =>
          Sync[F].blocking {
            val hash = sha256(content)
            val hashFile = cacheDir.resolve(sha256(relativePath.toString) + ".sha256")
            val cached = if Files.exists(hashFile) then Files.readString(hashFile).trim else ""

            if hash != cached then
              val target = outputDir.resolve(relativePath)
              Files.createDirectories(target.getParent)
              Files.writeString(target, content)
              Files.writeString(hashFile, hash)
              acc.copy(written = acc.written + 1)
            else
              acc.copy(skipped = acc.skipped + 1)
          }
        }

  private def sha256(s: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
