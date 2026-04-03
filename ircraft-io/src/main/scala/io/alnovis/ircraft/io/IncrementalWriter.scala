package io.alnovis.ircraft.io

import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Writes only files whose content has changed (based on SHA-256 hash). */
trait IncrementalWriter[F[_]]:
  def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult]

case class WriteResult(written: Int, skipped: Int, total: Int)

object IncrementalWriter:

  def apply[F[_]: Async]: IncrementalWriter[F] = new IncrementalWriter[F]:
    def writeChanged(outputDir: Path, files: Map[Path, String], cacheDir: Path): F[WriteResult] =
      Async[F].interruptible(Files.createDirectories(cacheDir)) *>
        files.toVector.parTraverse { (relativePath, content) =>
          val hash = sha256(content)
          Async[F].interruptible {
            val hashFile = cacheDir.resolve(sha256(relativePath.toString) + ".sha256")
            val cached = if Files.exists(hashFile) then Files.readString(hashFile).trim else ""

            if hash != cached then
              val target = outputDir.resolve(relativePath)
              Files.createDirectories(target.getParent)
              Files.writeString(target, content)
              Files.writeString(hashFile, hash)
              true
            else
              false
          }
        }.map { results =>
          val written = results.count(identity)
          WriteResult(written, results.size - written, results.size)
        }

  private def sha256(s: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
