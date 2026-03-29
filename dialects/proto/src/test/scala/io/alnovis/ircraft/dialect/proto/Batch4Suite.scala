package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class Batch4Suite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)

  // ── ValidationAnnotationsPass ──────────────────────────────────────────

  test("adds @NotNull to non-optional message-type getter"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("item", 1, TypeRef.NamedType("Item"))
      }
    }
    val module = Pipeline(
      "test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      ValidationAnnotationsPass
    ).run(IrModule("test", Vector(schema)), ctx).module

    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItem").get
    assert(getter.annotations.contains("NotNull"), s"Annotations: ${getter.annotations}")

  test("does not add @NotNull to optional field"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("notes", 1, TypeRef.STRING, optional = true)
      }
    }
    val module = Pipeline(
      "test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      ValidationAnnotationsPass
    ).run(IrModule("test", Vector(schema)), ctx).module

    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getNotes").get
    assert(!getter.annotations.contains("NotNull"))

  test("adds @NotNull to repeated field"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("items", 1, TypeRef.NamedType("Item"), repeated = true)
      }
    }
    val module = Pipeline(
      "test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      ValidationAnnotationsPass
    ).run(IrModule("test", Vector(schema)), ctx).module

    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItems").get
    assert(getter.annotations.contains("NotNull"))

  test("disabled when generateValidationAnnotations not set"):
    val ctx = PassContext()
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("item", 1, TypeRef.NamedType("Item"))
      }
    }
    val module = Pipeline(
      "test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      ValidationAnnotationsPass
    ).run(IrModule("test", Vector(schema)), ctx).module

    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItem").get
    assert(!getter.annotations.contains("NotNull"))
