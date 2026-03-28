package io.alnovis.ircraft.core.cache

import java.nio.file.{ Files, Path }

class IncrementalCacheIOSuite extends munit.FunSuite:

  val tmpDir: FunFixture[Path] = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ircraft-cache-test"),
    teardown = dir => Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))
  )

  val sampleCache: IncrementalCache = IncrementalCache(
    version = "1",
    entities = Map(
      "Money"    -> CacheEntry(12345, Set("com/example/Money.java", "com/example/AbstractMoney.java")),
      "Currency" -> CacheEntry(-67890, Set("com/example/Currency.java"))
    ),
    globalFiles = Set("com/example/ProtoWrapper.java", "com/example/VersionContext.java")
  )

  // -- round-trip ------------------------------------------------------------

  tmpDir.test("save then load produces identical cache") { dir =>
    IncrementalCacheIO.save(dir, sampleCache)
    val loaded = IncrementalCacheIO.load(dir)
    assertEquals(loaded, Some(sampleCache))
  }

  tmpDir.test("round-trip with empty cache") { dir =>
    val empty = IncrementalCache.empty
    IncrementalCacheIO.save(dir, empty)
    val loaded = IncrementalCacheIO.load(dir)
    assertEquals(loaded, Some(empty))
  }

  tmpDir.test("round-trip with single entity, no global files") { dir =>
    val cache = IncrementalCache("1", Map("Msg" -> CacheEntry(42, Set("Msg.java"))), Set.empty)
    IncrementalCacheIO.save(dir, cache)
    assertEquals(IncrementalCacheIO.load(dir), Some(cache))
  }

  // -- load edge cases -------------------------------------------------------

  tmpDir.test("load returns None when file missing") { dir =>
    assertEquals(IncrementalCacheIO.load(dir), None)
  }

  tmpDir.test("load returns None for corrupt JSON") { dir =>
    val file = dir.resolve(IncrementalCache.CacheFileName)
    Files.writeString(file, "not json at all")
    assertEquals(IncrementalCacheIO.load(dir), None)
  }

  // -- save creates directories ----------------------------------------------

  tmpDir.test("save creates parent directories") { dir =>
    val nested = dir.resolve("a/b/c")
    IncrementalCacheIO.save(nested, sampleCache)
    val loaded = IncrementalCacheIO.load(nested)
    assertEquals(loaded, Some(sampleCache))
  }

  // -- save overwrites existing cache ----------------------------------------

  tmpDir.test("save overwrites existing cache file") { dir =>
    IncrementalCacheIO.save(dir, sampleCache)
    val updated = sampleCache.update("NewMsg", 999, Set("NewMsg.java"))
    IncrementalCacheIO.save(dir, updated)
    val loaded = IncrementalCacheIO.load(dir)
    assertEquals(loaded, Some(updated))
  }

  // -- negative hash ---------------------------------------------------------

  tmpDir.test("round-trip preserves negative content hash") { dir =>
    val cache = IncrementalCache("1", Map("X" -> CacheEntry(-2147483648, Set("X.java"))), Set.empty)
    IncrementalCacheIO.save(dir, cache)
    assertEquals(IncrementalCacheIO.load(dir), Some(cache))
  }

  // -- special characters in names -------------------------------------------

  tmpDir.test("round-trip with special characters in entity name and path") { dir =>
    val cache = IncrementalCache(
      "1",
      Map("com.example.Money" -> CacheEntry(1, Set("com/example/Money.java"))),
      Set("path/with spaces/Global.java")
    )
    IncrementalCacheIO.save(dir, cache)
    assertEquals(IncrementalCacheIO.load(dir), Some(cache))
  }
