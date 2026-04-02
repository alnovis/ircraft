package io.alnovis.ircraft.io

import cats.effect.*
import java.nio.file.{Files, Path}
import munit.CatsEffectSuite

class CodeWriterSuite extends CatsEffectSuite:

  private val tmpDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ircraft-test-"),
    teardown = dir => deleteRecursive(dir)
  )

  private def deleteRecursive(path: Path): Unit =
    if Files.isDirectory(path) then
      Files.list(path).forEach(deleteRecursive)
    Files.deleteIfExists(path)

  tmpDir.test("CodeWriter writes files to disk") { dir =>
    val writer = CodeWriter[IO]
    val files = Map(
      Path.of("com/example/User.java") -> "package com.example;\npublic class User {}\n",
      Path.of("com/example/Order.java") -> "package com.example;\npublic class Order {}\n",
    )
    for
      count <- writer.write(dir, files)
      _ = assertEquals(count, 2)
      user <- IO.delay(Files.readString(dir.resolve("com/example/User.java")))
      _ = assert(user.contains("public class User"))
      order <- IO.delay(Files.readString(dir.resolve("com/example/Order.java")))
      _ = assert(order.contains("public class Order"))
    yield ()
  }

  tmpDir.test("IncrementalWriter skips unchanged files") { dir =>
    val writer = IncrementalWriter[IO]
    val cacheDir = dir.resolve(".cache")
    val files = Map(
      Path.of("A.java") -> "class A {}",
      Path.of("B.java") -> "class B {}",
    )

    for
      r1 <- writer.writeChanged(dir, files, cacheDir)
      _ = assertEquals(r1.written, 2)
      _ = assertEquals(r1.skipped, 0)

      // same content -- should skip both
      r2 <- writer.writeChanged(dir, files, cacheDir)
      _ = assertEquals(r2.written, 0)
      _ = assertEquals(r2.skipped, 2)

      // change one file
      changed = files.updated(Path.of("A.java"), "class A { int x; }")
      r3 <- writer.writeChanged(dir, changed, cacheDir)
      _ = assertEquals(r3.written, 1)
      _ = assertEquals(r3.skipped, 1)
    yield ()
  }
