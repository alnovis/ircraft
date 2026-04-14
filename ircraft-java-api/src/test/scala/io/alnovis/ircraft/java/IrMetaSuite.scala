package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir.{ Doc, Meta }
import munit.FunSuite

class IrMetaSuite extends FunSuite {

  private val strKey = Meta.Key[String]("test.str")
  private val intKey = Meta.Key[Int]("test.int")

  test("empty is empty") {
    assert(IrMeta.empty.isEmpty)
  }

  test("set + get returns value") {
    val m = IrMeta.empty.set(strKey, "hello")
    assertEquals(m.get(strKey).orElse(""), "hello")
  }

  test("get missing key returns empty") {
    assert(IrMeta.empty.get(strKey).isEmpty)
  }

  test("contains after set") {
    val m = IrMeta.empty.set(strKey, "val")
    assert(m.contains(strKey))
    assert(!m.contains(intKey))
  }

  test("remove removes key") {
    val m = IrMeta.empty.set(strKey, "val").remove(strKey)
    assert(m.isEmpty)
    assert(!m.contains(strKey))
  }

  test("set overwrites previous value") {
    val m = IrMeta.empty.set(strKey, "a").set(strKey, "b")
    assertEquals(m.get(strKey).orElse(""), "b")
  }

  test("of with single entry") {
    val m = IrMeta.of(strKey, "test")
    assertEquals(m.get(strKey).orElse(""), "test")
  }

  test("of with two entries") {
    val m = IrMeta.of(strKey, "hello", intKey, 42)
    assertEquals(m.get(strKey).orElse(""), "hello")
    assertEquals(m.get(intKey).orElse(0), 42)
  }

  test("roundtrip toScala -> fromScala") {
    val m            = IrMeta.of(strKey, "round")
    val roundtripped = IrMeta.fromScala(m.toScala)
    assertEquals(roundtripped.get(strKey).orElse(""), "round")
  }

  test("Doc key integration") {
    val doc = Doc("summary")
    val m   = IrMeta.empty.set(Doc.key, doc)
    assert(m.get(Doc.key).isPresent)
    assertEquals(m.get(Doc.key).get().summary, "summary")
  }

  test("not empty after set") {
    assert(!IrMeta.empty.set(strKey, "x").isEmpty)
  }
}
