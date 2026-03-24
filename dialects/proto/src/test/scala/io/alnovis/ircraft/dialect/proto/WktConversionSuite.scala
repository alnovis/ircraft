package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class WktConversionSuite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  private def lowerWithWkt(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): Module =
    Pipeline("test", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, WktConversionPass)
      .run(Module("test", Vector(schema)), ctx)
      .module

  test("Timestamp field becomes Instant"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Event") { m =>
        m.field("created_at", 1, TypeRef.NamedType("google.protobuf.Timestamp"))
      }
    }
    val module = lowerWithWkt(schema)
    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Event").get
    val getter = iface.methods.find(_.name == "getCreatedAt").get

    assertEquals(getter.returnType, TypeRef.NamedType("java.time.Instant"))

  test("Duration field becomes java.time.Duration"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Task") { m =>
        m.field("timeout", 1, TypeRef.NamedType("google.protobuf.Duration"))
      }
    }
    val module = lowerWithWkt(schema)
    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Task").get
    val getter = iface.methods.find(_.name == "getTimeout").get

    assertEquals(getter.returnType, TypeRef.NamedType("java.time.Duration"))

  test("Struct field becomes Map"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Config") { m =>
        m.field("metadata", 1, TypeRef.NamedType("google.protobuf.Struct"))
      }
    }
    val module = lowerWithWkt(schema)
    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Config").get
    val getter = iface.methods.find(_.name == "getMetadata").get

    assertEquals(getter.returnType, TypeRef.MapType(TypeRef.STRING, TypeRef.NamedType("Object")))

  test("non-WKT field unchanged"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Simple") { m =>
        m.field("name", 1, TypeRef.STRING)
      }
    }
    val module = lowerWithWkt(schema)
    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Simple").get
    val getter = iface.methods.find(_.name == "getName").get

    assertEquals(getter.returnType, TypeRef.STRING)

  test("WKT conversion applies to abstract class too"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Event") { m =>
        m.field("created_at", 1, TypeRef.NamedType("google.protobuf.Timestamp"))
      }
    }
    val module = lowerWithWkt(schema)
    val absCls = module.collect { case c: ClassOp => c }.find(c => c.isAbstract && c.name == "AbstractEvent").get

    val extractMethod = absCls.methods.find(_.name == "extractCreatedAt").get
    assertEquals(extractMethod.returnType, TypeRef.NamedType("java.time.Instant"))

    val getterImpl = absCls.methods.find(_.name == "getCreatedAt").get
    assertEquals(getterImpl.returnType, TypeRef.NamedType("java.time.Instant"))
