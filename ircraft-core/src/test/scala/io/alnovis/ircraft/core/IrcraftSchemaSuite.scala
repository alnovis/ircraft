package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.IrcraftSchema.*
import io.alnovis.ircraft.core.Traversal.*

class IrcraftSchemaSuite extends munit.FunSuite:

  // ── Test types ───────────────────────────────────────────────────────

  case class SimpleEntry(key: String, value: String) derives IrcraftSchema

  case class AllPrimitives(s: String, i: Int, l: Long, b: Boolean, ss: List[String]) derives IrcraftSchema

  case class Inner(x: String) derives IrcraftSchema
  case class Outer(name: String, inner: Inner) derives IrcraftSchema

  case class L3(z: Int) derives IrcraftSchema
  case class L2(y: String, l3: L3) derives IrcraftSchema
  case class L1(x: String, l2: L2) derives IrcraftSchema

  // ── Schema derivation ───────────────────────────────────────────────

  test("derive schema for flat case class"):
    val schema = IrcraftSchema[SimpleEntry]
    assertEquals(schema.opName, "simpleentry")
    assertEquals(schema.fieldSchemas, Vector("key" -> FieldType.StringField, "value" -> FieldType.StringField))
    assert(schema.childSlots.isEmpty)

  test("derive schema with all primitive types"):
    val schema = IrcraftSchema[AllPrimitives]
    assertEquals(schema.opName, "allprimitives")
    assertEquals(schema.fieldSchemas.size, 5)

  test("derive schema with nested type"):
    val schema = IrcraftSchema[Outer]
    assertEquals(schema.opName, "outer")
    assertEquals(schema.fieldSchemas, Vector("name" -> FieldType.StringField))
    assertEquals(schema.childSlots, Vector("inner"))

  // ── toOp ────────────────────────────────────────────────────────────

  test("toOp encodes flat case class"):
    val op = SimpleEntry("host", "localhost").toOp
    assertEquals(op.kind, NodeKind("derived", "simpleentry"))
    assertEquals(op.stringField("key"), Some("host"))
    assertEquals(op.stringField("value"), Some("localhost"))
    assert(op.regions.isEmpty)

  test("toOp encodes all primitive types"):
    val op = AllPrimitives("hello", 42, 123456789L, true, List("a", "b")).toOp
    assertEquals(op.stringField("s"), Some("hello"))
    assertEquals(op.intField("i"), Some(42))
    assertEquals(op.longField("l"), Some(123456789L))
    assertEquals(op.boolField("b"), Some(true))
    assertEquals(op.stringListField("ss"), Some(List("a", "b")))

  test("toOp encodes nested case class as region"):
    val op = Outer("test", Inner("nested")).toOp
    assertEquals(op.stringField("name"), Some("test"))
    assert(op.region("inner").isDefined)
    val innerOp = op.region("inner").get.operations.head.asInstanceOf[GenericOp]
    assertEquals(innerOp.stringField("x"), Some("nested"))
    assertEquals(innerOp.kind, NodeKind("derived", "inner"))

  test("toOp with custom namespace"):
    val op = SimpleEntry("k", "v").toOp("myapp")
    assertEquals(op.kind, NodeKind("myapp", "simpleentry"))

  // ── fromOp / round-trip ─────────────────────────────────────────────

  test("round-trip flat case class"):
    val original = SimpleEntry("host", "localhost")
    assertEquals(original.toOp.to[SimpleEntry], original)

  test("round-trip all primitive types"):
    val original = AllPrimitives("hello", 42, 123456789L, true, List("a", "b"))
    assertEquals(original.toOp.to[AllPrimitives], original)

  test("round-trip nested case class"):
    val original = Outer("test", Inner("nested"))
    assertEquals(original.toOp.to[Outer], original)

  test("round-trip three levels of nesting"):
    val original = L1("a", L2("b", L3(42)))
    assertEquals(original.toOp.to[L1], original)

  // ── Error handling ──────────────────────────────────────────────────

  test("fromOp with missing field throws"):
    val op = GenericOp(NodeKind("derived", "simpleentry"), AttributeMap(Attribute.StringAttr("key", "k")))
    intercept[IllegalArgumentException]:
      op.to[SimpleEntry]

  // ── Dialect generation ──────────────────────────────────────────────

  test("dialect generation from schemas"):
    val dialect = IrcraftSchema.dialect("test", IrcraftSchema[SimpleEntry], IrcraftSchema[Inner])
    assertEquals(dialect.namespace, "test")
    assert(dialect.schema("simpleentry").isDefined)
    assert(dialect.schema("inner").isDefined)
    assert(dialect.schema("simpleentry").get.isLeaf)

  test("dialect generation with nested type creates container"):
    val dialect = IrcraftSchema.dialect("test", IrcraftSchema[Outer])
    val outerSchema = dialect.schema("outer")
    assert(outerSchema.isDefined)
    assert(outerSchema.get.isContainer)
    assertEquals(outerSchema.get.childSlots, Vector("inner"))

  // ── Integration with Traversal ──────────────────────────────────────

  test("derived ops work with walkAll"):
    val op     = Outer("test", Inner("nested")).toOp
    val module = Module("test", Vector(op))
    var count  = 0
    module.walkAll {
      case _: GenericOp => count += 1
      case _            => ()
    }
    assertEquals(count, 2) // outer + inner

  test("derived ops work with transform"):
    val op      = SimpleEntry("old", "value").toOp
    val module  = Module("test", Vector(op))
    val updated = module.transform {
      case g: GenericOp if g.is("simpleentry") => g.withField("key", "new")
    }
    val result = updated.topLevel.head.asInstanceOf[GenericOp]
    assertEquals(result.stringField("key"), Some("new"))
