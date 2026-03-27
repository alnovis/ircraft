package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.Traversal.*

class GenericDialectSuite extends munit.FunSuite:

  val ConfigDialect: GenericDialect = GenericDialect("config"):
    leaf("entry", "key"    -> StringField, "value" -> StringField, "type" -> StringField)
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
    val entry1  = ConfigDialect.create("entry", "key" -> "host", "value" -> "localhost", "type" -> "string")
    val entry2  = ConfigDialect.create("entry", "key" -> "port", "value" -> "8080", "type" -> "int")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "server"), "entries" -> Vector(entry1, entry2))
    assertEquals(section.stringField("name"), Some("server"))
    assertEquals(section.children("entries").size, 2)

  test("nested containers"):
    val entry   = ConfigDialect.create("entry", "key" -> "x", "value" -> "y", "type" -> "z")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "db"), "entries" -> Vector(entry))
    val root    = ConfigDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))
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
    val entry  = ConfigDialect.create("entry", "key" -> "x", "value" -> "y", "type" -> "z")
    val scalar = ConfigDialect.create("scalar", "value" -> "y")
    assertNotEquals(entry.contentHash, scalar.contentHash)

  // ── Traversal compatibility ────────────────────────────────────────────

  test("walk traverses GenericOp tree"):
    val entry   = ConfigDialect.create("entry", "key" -> "host", "value" -> "v", "type" -> "t")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "srv"), "entries" -> Vector(entry))
    val root    = ConfigDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))

    val module = Module("test", Vector(root))
    var kinds  = List.empty[String]
    module.walkAll:
      case op: GenericOp => kinds = kinds :+ op.opName
      case _             => ()
    assertEquals(kinds, List("root", "section", "entry"))

  test("collectAll works on GenericOp"):
    val e1      = ConfigDialect.create("entry", "key" -> "a", "value" -> "1", "type" -> "s")
    val e2      = ConfigDialect.create("entry", "key" -> "b", "value" -> "2", "type" -> "s")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "x"), "entries" -> Vector(e1, e2))
    val module  = Module("test", Vector(section))

    val keys = module.topLevel.flatMap(_.collectAll:
      case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
        op.stringField("key").getOrElse(""))
    assertEquals(keys.toSet, Set("a", "b"))

  test("deepTransform works on GenericOp"):
    val entry   = ConfigDialect.create("entry", "key" -> "old", "value" -> "v", "type" -> "t")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "s"), "entries" -> Vector(entry))

    val transformed = section.deepTransform:
      case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
        op.copy(attributes = op.attributes + Attribute.StringAttr("key", "new"))

    val updatedEntry = transformed.asInstanceOf[GenericOp].children("entries").head.asInstanceOf[GenericOp]
    assertEquals(updatedEntry.stringField("key"), Some("new"))

  // ── Verify ─────────────────────────────────────────────────────────────

  test("verify reports missing fields"):
    val incomplete = GenericOp(ConfigDialect.kind("entry"), AttributeMap(Attribute.StringAttr("key", "x")))
    val diags      = ConfigDialect.verify(incomplete)
    assert(diags.exists(_.message.contains("missing fields")))

  test("verify passes for complete op"):
    val complete = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val diags    = ConfigDialect.verify(complete)
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

  // ── GenericOp.is / isOf ──────────────────────────────────────────────

  test("is matches operation name"):
    val entry = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    assert(entry.is("entry"))
    assert(!entry.is("section"))

  test("isOf matches dialect"):
    val entry = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    assert(entry.isOf(ConfigDialect))
    val other = GenericDialect("other"):
      leaf("x", "a" -> StringField)
    assert(!entry.isOf(other))

  // ── GenericOp.withField / withFields / without ───────────────────────

  test("withField adds a new field"):
    val entry   = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val updated = entry.withField("extra", "hello")
    assertEquals(updated.stringField("extra"), Some("hello"))
    assertEquals(updated.stringField("key"), Some("k"))

  test("withField replaces an existing field"):
    val entry   = ConfigDialect.create("entry", "key" -> "old", "value" -> "v", "type" -> "t")
    val updated = entry.withField("key", "new")
    assertEquals(updated.stringField("key"), Some("new"))

  test("withField supports int and boolean"):
    val entry = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    assertEquals(entry.withField("count", 42).intField("count"), Some(42))
    assertEquals(entry.withField("active", true).boolField("active"), Some(true))

  test("withFields updates multiple fields"):
    val entry   = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val updated = entry.withFields("key" -> "new", "value" -> "updated")
    assertEquals(updated.stringField("key"), Some("new"))
    assertEquals(updated.stringField("value"), Some("updated"))

  test("without removes a field"):
    val entry   = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val updated = entry.without("type")
    assertEquals(updated.stringField("type"), None)
    assertEquals(updated.stringField("key"), Some("k"))

  // ── GenericOp.withRegion ─────────────────────────────────────────────

  test("withRegion replaces existing region"):
    val e1      = ConfigDialect.create("entry", "key" -> "a", "value" -> "1", "type" -> "s")
    val e2      = ConfigDialect.create("entry", "key" -> "b", "value" -> "2", "type" -> "s")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "x"), "entries" -> Vector(e1))
    val updated = section.withRegion("entries", Vector(e1, e2))
    assertEquals(updated.children("entries").size, 2)

  test("withRegion adds new region"):
    val entry   = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val updated = entry.withRegion("extras", Vector.empty)
    assert(updated.region("extras").isDefined)

  // ── Extractors ───────────────────────────────────────────────────────

  test("extractor matches correct op kind"):
    val Entry = ConfigDialect.extractor("entry")
    val entry = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    entry match
      case Entry(e) => assertEquals(e.stringField("key"), Some("k"))
      case _        => fail("Extractor should match")

  test("extractor does not match different kind"):
    val Entry   = ConfigDialect.extractor("entry")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "x"), "entries" -> Vector.empty)
    section match
      case Entry(_) => fail("Should not match")
      case _        => ()

  test("extractor works in module.transform"):
    val Entry   = ConfigDialect.extractor("entry")
    val entry   = ConfigDialect.create("entry", "key" -> "old", "value" -> "v", "type" -> "t")
    val section = ConfigDialect.createContainer("section", Seq("name" -> "s"), "entries" -> Vector(entry))
    val module  = Module("test", Vector(section))
    val transformed = module.transform:
      case Entry(e) => e.withField("key", "new")
    val updated = transformed.topLevel.head.asInstanceOf[GenericOp].children("entries").head.asInstanceOf[GenericOp]
    assertEquals(updated.stringField("key"), Some("new"))

  test("extractor rejects unknown op name"):
    intercept[IllegalArgumentException]:
      ConfigDialect.extractor("nonexistent")

  // ── Unified apply() ──────────────────────────────────────────────────

  test("apply creates leaf ops"):
    val entry = ConfigDialect("entry", "key" -> "host", "value" -> "v", "type" -> "t")
    assertEquals(entry.stringField("key"), Some("host"))
    assert(entry.regions.isEmpty)

  test("apply creates container ops with children"):
    val entry   = ConfigDialect("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val section = ConfigDialect("section", "name" -> "db", "entries" -> Vector(entry))
    assertEquals(section.stringField("name"), Some("db"))
    assertEquals(section.children("entries").size, 1)

  test("apply creates container with empty children"):
    val root = ConfigDialect("root", "sections" -> Vector.empty[Operation])
    assertEquals(root.children("sections").size, 0)

  // ── transformPass ────────────────────────────────────────────────────

  test("transformPass creates a working pass"):
    val uppercaseKeys = ConfigDialect.transformPass("uppercase"):
      case e if e.is("entry") =>
        e.withField("key", e.stringField("key").getOrElse("").toUpperCase)
    val entry  = ConfigDialect.create("entry", "key" -> "host", "value" -> "v", "type" -> "t")
    val module = Module("test", Vector(entry))
    val result = uppercaseKeys.run(module, PassContext())
    assert(result.isSuccess)
    val updated = result.module.topLevel.head.asInstanceOf[GenericOp]
    assertEquals(updated.stringField("key"), Some("HOST"))

  test("transformPass only affects ops in the dialect"):
    val otherDialect = GenericDialect("other"):
      leaf("item", "name" -> StringField)
    val pass = ConfigDialect.transformPass("noop"):
      case e if e.is("item") => e.withField("name", "changed")
    val item   = otherDialect.create("item", "name" -> "original")
    val module = Module("test", Vector(item))
    val result = pass.run(module, PassContext())
    val unchanged = result.module.topLevel.head.asInstanceOf[GenericOp]
    assertEquals(unchanged.stringField("name"), Some("original"))

  test("transformPass works in pipeline"):
    val pass = ConfigDialect.transformPass("add-flag"):
      case e if e.is("entry") => e.withField("done", true)
    val pipeline = Pipeline("test", pass)
    val entry    = ConfigDialect.create("entry", "key" -> "k", "value" -> "v", "type" -> "t")
    val result   = pipeline.run(Module("test", Vector(entry)), PassContext())
    assert(result.isSuccess)
    val updated = result.module.topLevel.head.asInstanceOf[GenericOp]
    assertEquals(updated.boolField("done"), Some(true))
