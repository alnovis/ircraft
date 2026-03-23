package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class Batch4Suite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)

  // ── ValidationAnnotationsPass ──────────────────────────────────────────

  test("adds @NotNull to non-optional message-type getter"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("item", 1, TypeRef.NamedType("Item"))
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, ValidationAnnotationsPass
    ).run(Module("test", Vector(schema)), ctx).module

    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItem").get
    assert(getter.annotations.contains("NotNull"), s"Annotations: ${getter.annotations}")

  test("does not add @NotNull to optional field"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("notes", 1, TypeRef.STRING, optional = true)
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, ValidationAnnotationsPass
    ).run(Module("test", Vector(schema)), ctx).module

    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getNotes").get
    assert(!getter.annotations.contains("NotNull"))

  test("adds @NotNull to repeated field"):
    val ctx = PassContext(config = Map("generateValidationAnnotations" -> "true"))
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("items", 1, TypeRef.NamedType("Item"), repeated = true)
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, ValidationAnnotationsPass
    ).run(Module("test", Vector(schema)), ctx).module

    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItems").get
    assert(getter.annotations.contains("NotNull"))

  test("disabled when generateValidationAnnotations not set"):
    val ctx = PassContext()
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("item", 1, TypeRef.NamedType("Item"))
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, ValidationAnnotationsPass
    ).run(Module("test", Vector(schema)), ctx).module

    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get
    val getter = iface.methods.find(_.name == "getItem").get
    assert(!getter.annotations.contains("NotNull"))

  // ── SchemaMetadataPass ─────────────────────────────────────────────────

  test("generates SchemaInfo per version"):
    val ctx = PassContext(config = Map("generateSchemaMetadata" -> "true"))
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, SchemaMetadataPass
    ).run(Module("test", Vector(schema)), ctx).module

    val v1Info = module.collect { case c: ClassOp => c }.find(_.name == "SchemaInfoV1")
    val v2Info = module.collect { case c: ClassOp => c }.find(_.name == "SchemaInfoV2")
    assert(v1Info.isDefined, "Should generate SchemaInfoV1")
    assert(v2Info.isDefined, "Should generate SchemaInfoV2")

    val methods = v1Info.get.methods.map(_.name)
    assert(methods.contains("getVersionId"))
    assert(methods.contains("getMessageNames"))
    assert(methods.contains("getEnumNames"))

  test("disabled when generateSchemaMetadata not set"):
    val ctx = PassContext()
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, SchemaMetadataPass
    ).run(Module("test", Vector(schema)), ctx).module

    val infoClasses = module.collect { case c: ClassOp => c }.filter(_.name.startsWith("SchemaInfo"))
    assert(infoClasses.isEmpty)

  // ── Full pipeline with all passes ──────────────────────────────────────

  test("all passes compose into full pipeline"):
    val ctx = PassContext(config = Map(
      "generateBuilders" -> "true",
      "generateValidationAnnotations" -> "true",
      "generateSchemaMetadata" -> "true",
    ))
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG, optional = true)
        m.field("currency", 2, TypeRef.STRING)
      }
      s.enum_("Currency") { e =>
        e.value("USD", 0)
        e.value("EUR", 1)
      }
    }

    val pipeline = Pipeline("full-pipeline",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      ConflictResolutionPass,
      HasMethodsPass,
      ProtoWrapperPass,
      CommonMethodsPass,
      VersionContextPass,
      ProtocolVersionsPass,
      VersionConversionPass,
      BuilderPass,
      WktConversionPass,
      ValidationAnnotationsPass,
      SchemaMetadataPass,
    )
    val result = pipeline.run(Module("test", Vector(schema)), ctx)
    assert(result.isSuccess, s"Pipeline failed: ${result.diagnostics}")

    val module = result.module
    val allTypes = module.collect { case c: ClassOp => c }.map(_.name) ++
      module.collect { case i: InterfaceOp => i }.map(_.name) ++
      module.collect { case e: EnumClassOp => e }.map(_.name)

    // Core types
    assert(allTypes.contains("Money"))
    assert(allTypes.contains("AbstractMoney"))
    assert(allTypes.contains("MoneyV1"))
    assert(allTypes.contains("MoneyV2"))
    assert(allTypes.contains("Currency"))

    // Infrastructure
    assert(allTypes.contains("ProtoWrapper"))
    assert(allTypes.contains("VersionContext"))
    assert(allTypes.contains("VersionContextV1"))
    assert(allTypes.contains("VersionContextV2"))
    assert(allTypes.contains("ProtocolVersions"))

    // Metadata
    assert(allTypes.contains("SchemaInfoV1"))
    assert(allTypes.contains("SchemaInfoV2"))

    // Builder on interface
    val moneyIface = module.collect { case i: InterfaceOp => i }.find(_.name == "Money").get
    assert(moneyIface.nestedTypes.exists {
      case i: InterfaceOp => i.name == "Builder"
      case _              => false
    })

    // has/supports
    assert(moneyIface.methods.exists(_.name == "hasAmount"))

    // version conversion
    assert(moneyIface.methods.exists(_.name == "asVersion"))
