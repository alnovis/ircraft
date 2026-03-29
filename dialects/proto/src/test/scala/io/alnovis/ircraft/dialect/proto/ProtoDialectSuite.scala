package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass
import io.alnovis.ircraft.dialect.proto.types.*

class ProtoDialectSuite extends munit.FunSuite:

  val ctx: PassContext = PassContext()

  test("DSL builds a valid schema"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
      s.enum_("Currency") { e =>
        e.value("USD", 0)
        e.value("EUR", 1)
      }
    }

    assertEquals(schema.versions, List("v1", "v2"))
    assertEquals(schema.messages.size, 1)
    assertEquals(schema.messages.head.name, "Money")
    assertEquals(schema.messages.head.fields.size, 2)
    assertEquals(schema.messages.head.fields(0).javaName, "amount")
    assertEquals(schema.messages.head.fields(1).javaName, "currency")
    assertEquals(schema.enums.size, 1)
    assertEquals(schema.enums.head.values.size, 2)

  test("DSL builds schema with oneof"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Event") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.oneof("payload") { o =>
          o.field("text", 2, TypeRef.STRING)
          o.field("number", 3, TypeRef.INT)
        }
      }
    }

    val msg = schema.messages.head
    assertEquals(msg.oneofs.size, 1)
    assertEquals(msg.oneofs.head.protoName, "payload")
    assertEquals(msg.oneofs.head.javaName, "payload")
    assertEquals(msg.oneofs.head.caseEnumName, "PayloadCase")
    assertEquals(msg.oneofs.head.fields.size, 2)

  test("DSL builds schema with nested messages"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.nestedMessage("Item") { n =>
          n.field("name", 1, TypeRef.STRING)
          n.field("quantity", 2, TypeRef.INT)
        }
      }
    }

    val order = schema.messages.head
    assertEquals(order.nestedMessages.size, 1)
    assertEquals(order.nestedMessages.head.name, "Item")
    assertEquals(order.nestedMessages.head.fields.size, 2)

  test("DSL builds field with conflict type"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Payment") { m =>
        m.field("type", 1, TypeRef.INT, conflictType = ConflictType.IntEnum)
      }
    }

    val field = schema.messages.head.fields.head
    assert(field.hasConflict)
    assertEquals(field.conflictType, ConflictType.IntEnum)

  test("verifier passes for valid schema"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("bar", 1, TypeRef.STRING)
      }
    }

    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.isSuccess, s"Expected success but got: ${result.diagnostics}")

  test("verifier catches empty versions"):
    val schema = SchemaOp(
      versions = Nil,
      versionSyntax = Map.empty,
      regions = Vector.empty,
      attributes = AttributeMap.empty,
      span = None
    )
    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.hasErrors)
    assert(result.diagnostics.exists(_.message.contains("at least one version")))

  test("verifier catches empty message name"):
    val schema = SchemaOp(
      versions = List("v1"),
      messages = Vector(
        MessageOp("", Set("v1"), fields = Vector(FieldOp("f", "f", 1, TypeRef.INT, presentInVersions = Set("v1"))))
      )
    )
    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.hasErrors)
    assert(result.diagnostics.exists(_.message.contains("name must not be empty")))

  test("verifier catches non-positive field number"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("bar", 0, TypeRef.STRING)
      }
    }
    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.hasErrors)
    assert(result.diagnostics.exists(_.message.contains("positive number")))

  test("verifier catches duplicate field numbers"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("a", 1, TypeRef.STRING)
        m.field("b", 1, TypeRef.INT)
      }
    }
    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.hasErrors)
    assert(result.diagnostics.exists(_.message.contains("duplicate field numbers")))

  test("verifier catches empty oneof"):
    val schema = SchemaOp(
      versions = List("v1"),
      messages = Vector(
        MessageOp(
          "Foo",
          Set("v1"),
          oneofs = Vector(OneofOp("empty_oneof", "emptyOneof", "EmptyOneofCase", Set("v1"), fields = Vector.empty))
        )
      )
    )
    val module = IrModule("test", Vector(schema))
    val result = ProtoVerifierPass.run(module, ctx)
    assert(result.hasErrors)
    assert(result.diagnostics.exists(_.message.contains("at least one field")))

  test("content hash is deterministic"):
    val s1 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    val s2 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    assertEquals(s1.contentHash, s2.contentHash)

  test("content hash changes when field changes"):
    val s1 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    val s2 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.INT))
    }
    assertNotEquals(s1.contentHash, s2.contentHash)

  test("ProtoDialect owns proto operations"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    assert(ProtoDialect.owns(schema))
    assert(ProtoDialect.owns(schema.messages.head))
    assert(ProtoDialect.owns(schema.messages.head.fields.head))

  test("snakeToCamel conversion in DSL"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("my_field_name", 1, TypeRef.STRING)
      }
    }
    assertEquals(schema.messages.head.fields.head.javaName, "myFieldName")

  test("messageHashes returns per-message content hashes"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
      }
    }

    val hashes = schema.messageHashes
    assertEquals(hashes.size, 2)
    assert(hashes.contains("Money"))
    assert(hashes.contains("Order"))
    assertNotEquals(hashes("Money"), hashes("Order"))

  test("enumHashes returns per-enum content hashes"):
    val schema = ProtoSchema.build("v1") { s =>
      s.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
      }
      s.enum_("Currency") { e =>
        e.value("USD", 0)
        e.value("EUR", 1)
      }
    }

    val hashes = schema.enumHashes
    assertEquals(hashes.size, 2)
    assert(hashes.contains("Status"))
    assert(hashes.contains("Currency"))

  test("messageHashes is stable across identical builds"):
    def buildSchema() = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    assertEquals(buildSchema().messageHashes, buildSchema().messageHashes)

  test("messageHashes changes when field type changes"):
    val s1 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.STRING))
    }
    val s2 = ProtoSchema.build("v1") { s =>
      s.message("Foo")(m => m.field("bar", 1, TypeRef.INT))
    }
    assertNotEquals(s1.messageHashes("Foo"), s2.messageHashes("Foo"))

  test("pipeline with verifier pass"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val pipeline = Pipeline("proto-validation", ProtoVerifierPass)
    val result   = pipeline.run(IrModule("test", Vector(schema)), ctx)
    assert(result.isSuccess)
