package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class BuilderSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig("com.example.api", "com.example.%s", generateBuilders = true)
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  // Enable generateBuilders in PassContext
  val ctx: PassContext = PassContext(config = Map("generateBuilders" -> "true"))

  private def lowerWithBuilder(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): Module =
    Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      BuilderPass,
    ).run(Module("test", Vector(schema)), ctx).module

  test("interface gets nested Builder interface"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }
    val module = lowerWithBuilder(schema)
    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Money").get

    val builder = iface.nestedTypes.collectFirst { case i: InterfaceOp if i.name == "Builder" => i }
    assert(builder.isDefined, s"Should have nested Builder, got: ${iface.nestedTypes.map(_.getClass.getSimpleName)}")

    val builderMethods = builder.get.methods.map(_.name)
    assert(builderMethods.contains("setAmount"), s"Builder methods: $builderMethods")
    assert(builderMethods.contains("setCurrency"))
    assert(builderMethods.contains("clearAmount"))
    assert(builderMethods.contains("clearCurrency"))
    assert(builderMethods.contains("build"))

  test("interface gets toBuilder method"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = lowerWithBuilder(schema)
    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Money").get

    assert(iface.methods.exists(_.name == "toBuilder"))

  test("abstract class gets nested AbstractBuilder"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = lowerWithBuilder(schema)
    val absCls = module.collect { case c: ClassOp => c }.find(c => c.isAbstract && c.name == "AbstractMoney").get

    val absBuilder = absCls.nestedTypes.collectFirst { case c: ClassOp if c.name == "AbstractBuilder" => c }
    assert(absBuilder.isDefined, s"Should have nested AbstractBuilder")

    val methods = absBuilder.get.methods.map(_.name)
    assert(methods.contains("doSetAmount"), s"Methods: $methods")
    assert(methods.contains("doClearAmount"))
    assert(methods.contains("doBuild"))
    assert(methods.contains("setAmount")) // concrete setter
    assert(methods.contains("build"))     // concrete build

  test("impl class gets toBuilder"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = lowerWithBuilder(schema)
    val impl = module.collect { case c: ClassOp => c }.find(_.name == "MoneyV1").get

    assert(impl.methods.exists(_.name == "toBuilder"))

  test("BuilderPass disabled when generateBuilders=false"):
    val ctxDisabled = PassContext(config = Map.empty) // no generateBuilders
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val pipeline = Pipeline("test",
      io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass,
      lowering,
      BuilderPass,
    )
    val module = pipeline.run(Module("test", Vector(schema)), ctxDisabled).module
    val iface = module.collect { case i: InterfaceOp => i }.find(_.name == "Money").get

    // Should NOT have Builder
    assert(!iface.nestedTypes.exists {
      case i: InterfaceOp => i.name == "Builder"
      case _              => false
    })
