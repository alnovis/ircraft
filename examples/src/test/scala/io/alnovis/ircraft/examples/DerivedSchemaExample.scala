package io.alnovis.ircraft.examples

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.IrcraftCodec.*
import io.alnovis.ircraft.core.Traversal.*

/**
  * End-to-end example: Domain model -> IR -> codegen pipeline.
  *
  * Demonstrates `derives IrcraftSchema` workflow:
  *   1. Define domain types with `derives IrcraftSchema`
  *   2. Schema-only IR for code generation (IrcraftSchema.module)
  *   3. Data round-trip via IrcraftCodec (auto-derived)
  *   4. Extractors and passes directly from schema
  *   5. Integration with Pipeline and Traversal
  */
class DerivedSchemaExample extends munit.FunSuite:

  // ── 1. Domain types ──────────────────────────────────────────────────
  // Note: field names from case class params are used as-is (camelCase preserved)

  case class Field(name: String, kind: String, nullable: Boolean) derives IrcraftSchema
  case class Entity(name: String, table: String, pk: Field) derives IrcraftSchema

  // ── 2. Schema: type structure for codegen ────────────────────────────

  test("schema extracts type structure without data"):
    val schema = IrcraftSchema[Entity]
    assertEquals(schema.opName, "entity")
    assertEquals(schema.fieldSchemas, Vector(
      "name"  -> FieldType.StringField,
      "table" -> FieldType.StringField
    ))
    assertEquals(schema.childSlots, Vector("pk"))

  test("schema module creates codegen-ready IR"):
    val module = IrcraftSchema.module("orm", IrcraftSchema[Entity], IrcraftSchema[Field])
    assertEquals(module.topLevel.size, 2)

    val entityOp = module.topLevel.head.asInstanceOf[GenericOp]
    assertEquals(entityOp.kind, NodeKind("orm", "entity"))
    assertEquals(entityOp.stringField("name"), Some("StringField"))
    assertEquals(entityOp.stringField("table"), Some("StringField"))
    assert(entityOp.region("pk").get.operations.isEmpty)

  // ── 3. Codec: data round-trip ────────────────────────────────────────

  test("codec encodes domain objects to IR"):
    val id = Field("id", "BIGSERIAL", nullable = false)
    val user = Entity("User", "users", id)
    val op = user.toOp

    assertEquals(op.stringField("name"), Some("User"))
    assertEquals(op.stringField("table"), Some("users"))
    val pkOp = op.region("pk").get.operations.head.asInstanceOf[GenericOp]
    assertEquals(pkOp.stringField("name"), Some("id"))
    assertEquals(pkOp.stringField("kind"), Some("BIGSERIAL"))
    assertEquals(pkOp.boolField("nullable"), Some(false))

  test("codec round-trips nested domain objects"):
    val id = Field("id", "BIGSERIAL", nullable = false)
    val user = Entity("User", "users", id)
    assertEquals(user.toOp.to[Entity], user)

  // ── 4. Extractors and passes from schema ─────────────────────────────

  test("extractor matches only its type"):
    val EntityOp = IrcraftSchema[Entity].extractor
    val FieldOp = IrcraftSchema[Field].extractor

    val user = Entity("User", "users", Field("id", "BIGSERIAL", nullable = false))
    val op = user.toOp

    op match
      case EntityOp(e) => assertEquals(e.stringField("name"), Some("User"))
      case _           => fail("should match Entity")

    val pkOp = op.region("pk").get.operations.head
    pkOp match
      case FieldOp(f) => assertEquals(f.stringField("name"), Some("id"))
      case _          => fail("should match Field")

  test("transformPass modifies only target type"):
    val addPrefix = IrcraftSchema[Entity].transformPass("add-prefix"):
      case e => e.withField("table", "app_" + e.stringField("table").getOrElse(""))

    val module = Module("test", Vector(
      Entity("User", "users", Field("id", "BIGSERIAL", nullable = false)).toOp,
      Entity("Order", "orders", Field("id", "BIGSERIAL", nullable = false)).toOp
    ))

    val result = addPrefix.run(module, PassContext())
    val EntityOp = IrcraftSchema[Entity].extractor
    val names = result.module.topLevel.collect:
      case EntityOp(e) => e.stringField("table").get
    assertEquals(names, Vector("app_users", "app_orders"))

  // ── 5. Full pipeline ─────────────────────────────────────────────────

  test("full pipeline: derive -> transform -> inspect"):
    case class Col(name: String, kind: String) derives IrcraftSchema
    case class Tab(name: String, pk: Col) derives IrcraftSchema

    val ColOp = IrcraftSchema[Col].extractor

    val module = Module("db", Vector(
      Tab("users", Col("id", "serial")).toOp,
      Tab("orders", Col("order_id", "uuid")).toOp
    ))

    val normalizeTypes = IrcraftSchema[Col].transformPass("normalize"):
      case c => c.withField("kind", c.stringField("kind").getOrElse("").toUpperCase)

    val result = Pipeline("db-pipeline", normalizeTypes).run(module, PassContext())
    assert(result.isSuccess)

    val types = result.module.topLevel.flatMap(_.collectAll:
      case ColOp(c) => c.stringField("kind").get
    )
    assertEquals(types, Vector("SERIAL", "UUID"))

  // ── 6. Dialect generation from schemas ───────────────────────────────

  test("generate dialect for verify and extractor reuse"):
    val OrmDialect = IrcraftSchema.dialect("orm", IrcraftSchema[Entity], IrcraftSchema[Field])

    assertEquals(OrmDialect.namespace, "orm")
    assert(OrmDialect.schema("entity").isDefined)
    assert(OrmDialect.schema("field").isDefined)
    assert(OrmDialect.schema("entity").get.isContainer)
    assert(OrmDialect.schema("field").get.isLeaf)
