package io.alnovis.ircraft.core.cache

class IncrementalCacheSuite extends munit.FunSuite:

  val cache: IncrementalCache = IncrementalCache(
    version = "1",
    entities = Map(
      "Money"    -> CacheEntry(100, Set("com/example/Money.java", "com/example/AbstractMoney.java")),
      "Currency" -> CacheEntry(200, Set("com/example/Currency.java")),
      "Status"   -> CacheEntry(300, Set("com/example/Status.java"))
    ),
    globalFiles = Set("com/example/ProtoWrapper.java")
  )

  // -- unchangedEntities -----------------------------------------------------

  test("unchangedEntities returns names with matching hash"):
    val current = Map("Money" -> 100, "Currency" -> 200, "Status" -> 300)
    assertEquals(cache.unchangedEntities(current), Set("Money", "Currency", "Status"))

  test("unchangedEntities excludes changed hashes"):
    val current = Map("Money" -> 100, "Currency" -> 999, "Status" -> 300)
    assertEquals(cache.unchangedEntities(current), Set("Money", "Status"))

  test("unchangedEntities excludes new entities"):
    val current = Map("Money" -> 100, "NewMsg" -> 400)
    assertEquals(cache.unchangedEntities(current), Set("Money"))

  test("unchangedEntities returns empty when all changed"):
    val current = Map("Money" -> 999, "Currency" -> 999)
    assertEquals(cache.unchangedEntities(current), Set.empty[String])

  // -- changedEntities -------------------------------------------------------

  test("changedEntities returns changed and new names"):
    val current = Map("Money" -> 100, "Currency" -> 999, "NewMsg" -> 400)
    assertEquals(cache.changedEntities(current), Set("Currency", "Status", "NewMsg"))

  test("changedEntities returns all when hashes differ"):
    val current = Map("Money" -> 1, "Currency" -> 2, "Status" -> 3)
    assertEquals(cache.changedEntities(current), Set("Money", "Currency", "Status"))

  test("changedEntities returns empty when all match"):
    val current = Map("Money" -> 100, "Currency" -> 200, "Status" -> 300)
    assertEquals(cache.changedEntities(current), Set.empty[String])

  test("changedEntities includes removed entities"):
    val current = Map("Money" -> 100)
    assertEquals(cache.changedEntities(current), Set("Currency", "Status"))

  // -- update ----------------------------------------------------------------

  test("update adds new entity"):
    val updated = cache.update("NewMsg", 500, Set("com/example/NewMsg.java"))
    assertEquals(updated.entities("NewMsg"), CacheEntry(500, Set("com/example/NewMsg.java")))
    assertEquals(updated.entities.size, 4)

  test("update replaces existing entity"):
    val updated = cache.update("Money", 999, Set("com/example/Money2.java"))
    assertEquals(updated.entities("Money"), CacheEntry(999, Set("com/example/Money2.java")))
    assertEquals(updated.entities.size, 3)

  // -- withGlobalFiles -------------------------------------------------------

  test("withGlobalFiles replaces global files"):
    val updated = cache.withGlobalFiles(Set("a.java", "b.java"))
    assertEquals(updated.globalFiles, Set("a.java", "b.java"))
    assertEquals(updated.entities, cache.entities)

  // -- empty -----------------------------------------------------------------

  test("empty cache has current version"):
    val empty = IncrementalCache.empty
    assertEquals(empty.version, IncrementalCache.CurrentVersion)
    assertEquals(empty.entities, Map.empty[String, CacheEntry])
    assertEquals(empty.globalFiles, Set.empty[String])
