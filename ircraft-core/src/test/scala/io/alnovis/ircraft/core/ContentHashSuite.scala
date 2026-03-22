package io.alnovis.ircraft.core

class ContentHashSuite extends munit.FunSuite:

  test("combine is deterministic"):
    val h1 = ContentHash.combine(1, 2, 3)
    val h2 = ContentHash.combine(1, 2, 3)
    assertEquals(h1, h2)

  test("combine is sensitive to order"):
    val h1 = ContentHash.combine(1, 2, 3)
    val h2 = ContentHash.combine(3, 2, 1)
    assertNotEquals(h1, h2)

  test("ofString handles null"):
    assertEquals(ContentHash.ofString(null), 0)

  test("ofList produces same hash for same elements"):
    val h1 = ContentHash.ofList(List(1, 2, 3))
    val h2 = ContentHash.ofList(List(1, 2, 3))
    assertEquals(h1, h2)

  test("ofList differs for different elements"):
    val h1 = ContentHash.ofList(List(1, 2, 3))
    val h2 = ContentHash.ofList(List(1, 2, 4))
    assertNotEquals(h1, h2)

  test("ofSet is order-independent"):
    val h1 = ContentHash.ofSet(Set(1, 2, 3))
    val h2 = ContentHash.ofSet(Set(3, 1, 2))
    assertEquals(h1, h2)

  test("ContentHashable type class for String"):
    val hash = summon[ContentHashable[String]].contentHash("hello")
    assertEquals(hash, "hello".hashCode)
