package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.Traversal.*

class GenericDialectSuite extends munit.FunSuite:

  val ConfigDialect: GenericDialect = GenericDialect("config"):
    leaf("entry", "key" -> StringField, "value" -> StringField, "type" -> StringField)
    leaf("scalar", "value" -> StringField)
    container("section", "name" -> StringField)("entries")
    container("root")("sections")

  // ── Leaf creation and field access ─────────────────────────────────────

  test("create leaf and read fields"):
    val entry = ConfigDialect.create("entry", "key" -> "host", "value" -> "localhost", "type" -> "string")
    assertEquals(entry.stringField("key"), Some("host"))
    assertEquals(entry.stringField("value"), Some("localhost"))
    assertEquals(entry.kind, ConfigDialect.kind("entry"))

  test("create leaf with int field"):
    val Dialect2 = GenericDialect("nums"):
      leaf("item", "count" -> IntField)
    val item = Dialect2.create("item", "count" -> 42)
    assertEquals(item.intField("count"), Some(42))

  test("leaf has empty regions"):
    val entry = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    assert(entry.regions.isEmpty)

  // ── Container creation ─────────────────────────────────────────────────

  test("create container with children"):
    val entry1 = ConfigDialect.create("entry", "key" -> "host", "value" -> "localhost", "type" -> "string")
    val entry2 = ConfigDialect.create("entry", "key" -> "port", "value" -> "8080", "type" -> "int")
    val section = ConfigDialect.createContainer("section",
      Seq("name" -> "server"),
      "entries" -> Vector(entry1, entry2)
    )
    assertEquals(section.stringField("name"), Some("server"))
    assertEquals(section.children("entries").size, 2)

  test("nested containers"):
    val entry = ConfigDialect.create("entry", "key" -> "x", "value" -> "y", "type" -> "z")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "db"), "entries" -> Vector(entry))
    val root = ConfigDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))
    assertEquals(root.children("sections").size, 1)

  // ── ContentHash ────────────────────────────────────────────────────────

  test("same content produces same hash"):
    val a = ConfigDialect.create("entry", "key" -> "host", "value" -> "x", "type" -> "s")
    val b = ConfigDialect.create("entry", "key" -> "host", "value" -> "x", "type" -> "s")
    assertEquals(a.contentHash, b.contentHash)

  test("different content produces different hash"):
    val a = ConfigDialect.create("entry", "key" -> "host", "value" -> "x", "type" -> "s")
    val b = ConfigDialect.create("entry", "key" -> "port", "value" -> "x", "type" -> "s")
    assertNotEquals(a.contentHash, b.contentHash)

  test("different kinds produce different hash"):
    val entry = ConfigDialect.create("entry", "key" -> "x", "value" -> "y", "type" -> "z")
    val scalar = ConfigDialect.create("scalar", "value" -> "y")
    assertNotEquals(entry.contentHash, scalar.contentHash)

  // ── Traversal compatibility ────────────────────────────────────────────

  test("walk traverses GenericOp tree"):
    val entry = ConfigDialect.create("entry", "key" -> "host", "value" -> "v", "type" -> "t")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "srv"), "entries" -> Vector(entry))
    val root = ConfigDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))

    val module = Module("test", Vector(root))
    var kinds = List.empty[String]
    module.walkAll:
      case op: GenericOp => kinds = kinds :+ op.opName
      case _             => ()
    assertEquals(kinds, List("root", "section", "entry"))

  test("collectAll works on GenericOp"):
    val e1 = ConfigDialect.create("entry", "key" -> "a", "value" -> "1", "type" -> "s")
    val e2 = ConfigDialect.create("entry", "key" -> "b", "value" -> "2", "type" -> "s")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "x"), "entries" -> Vector(e1, e2))
    val module = Module("test", Vector(section))

    val keys = module.topLevel.flatMap(_.collectAll:
      case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
        op.stringField("key").getOrElse("")
    )
    assertEquals(keys.toSet, Set("a", "b"))

  test("deepTransform works on GenericOp"):
    val entry = ConfigDialect.create("entry", "key" -> "old", "value" -> "v", "type" -> "t")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "s"), "entries" -> Vector(entry))

    val transformed = section.deepTransform:
      case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
        op.copy(attributes = op.attributes + Attribute.StringAttr("key", "new"))

    val updatedEntry = transformed.asInstanceOf[GenericOp].children("entries").head.asInstanceOf[GenericOp]
    assertEquals(updatedEntry.stringField("key"), Some("new"))

  // ── Verify ─────────────────────────────────────────────────────────────

  test("verify reports missing fields"):
    val incomplete = GenericOp(ConfigDialect.kind("entry"), AttributeMap(Attribute.StringAttr("key", "x")))
    val diags = ConfigDialect.verify(incomplete)
    assert(diags.exists(_.message.contains("missing fields")))

  test("verify passes for complete op"):
    val complete = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val diags = ConfigDialect.verify(complete)
    assert(diags.isEmpty, s"Unexpected diagnostics: $diags")

  // ── Error cases ────────────────────────────────────────────────────────

  test("create with unknown op name throws"):
    intercept[IllegalArgumentException]:
      ConfigDialect.create("unknown", "x" -> "y")

  test("duplicate schema name throws"):
    intercept[IllegalArgumentException]:
      GenericDialect("bad"):
        leaf("dup", "x" -> StringField)
        leaf("dup", "y" -> StringField)
