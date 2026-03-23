package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class InfraPassesSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext = PassContext()

  private def runPipeline(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp, passes: Pass*): Module =
    val allPasses = Vector(io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering) ++ passes
    Pipeline("test", allPasses).run(Module("test", Vector(schema)), ctx).module

  // ── ProtocolVersionsPass ───────────────────────────────────────────────

  test("ProtocolVersionsPass generates version constants"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m => m.field("amount", 1, TypeRef.LONG) }
    }
    val module = runPipeline(schema, ProtocolVersionsPass)
    val pvClass = module.collect { case c: ClassOp => c }.find(_.name == "ProtocolVersions")

    assert(pvClass.isDefined, "Should generate ProtocolVersions")
    val cls = pvClass.get
    assert(cls.fields.exists(_.name == "V1"), s"Fields: ${cls.fields.map(_.name)}")
    assert(cls.fields.exists(_.name == "V2"))
    assert(cls.fields.exists(_.name == "DEFAULT"))

  // ── ProtoWrapperPass ───────────────────────────────────────────────────

  test("ProtoWrapperPass generates ProtoWrapper interface"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m => m.field("amount", 1, TypeRef.LONG) }
    }
    val module = runPipeline(schema, ProtoWrapperPass)
    val pw = module.collect { case i: InterfaceOp => i }.find(_.name == "ProtoWrapper")

    assert(pw.isDefined, "Should generate ProtoWrapper")
    assert(pw.get.methods.exists(_.name == "getTypedProto"))
    assert(pw.get.methods.exists(_.name == "getWrapperVersionId"))
    assert(pw.get.methods.exists(_.name == "toBytes"))

  test("ProtoWrapperPass updates message interfaces to extend ProtoWrapper"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m => m.field("amount", 1, TypeRef.LONG) }
    }
    val module = runPipeline(schema, ProtoWrapperPass)
    val moneyIface = module.collect { case i: InterfaceOp => i }.find(_.name == "Money")

    assert(moneyIface.isDefined)
    assert(
      moneyIface.get.extendsTypes.exists {
        case TypeRef.NamedType("ProtoWrapper") => true
        case _                                 => false
      },
      s"Should extend ProtoWrapper, extends: ${moneyIface.get.extendsTypes}"
    )

  // ── CommonMethodsPass ──────────────────────────────────────────────────

  test("CommonMethodsPass adds equals/hashCode/toString to abstract class"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m => m.field("amount", 1, TypeRef.LONG) }
    }
    val module = runPipeline(schema, CommonMethodsPass)
    val absCls = module.collect { case c: ClassOp => c }.find(c => c.isAbstract && c.name.startsWith("Abstract"))

    assert(absCls.isDefined)
    val methods = absCls.get.methods.map(_.name)
    assert(methods.contains("equals"), s"Methods: $methods")
    assert(methods.contains("hashCode"))
    assert(methods.contains("toString"))
    assert(methods.contains("toBytes"))
    assert(methods.contains("getTypedProto"))
    assert(methods.contains("getWrapperVersionId"))

  // ── VersionContextPass ─────────────────────────────────────────────────

  test("VersionContextPass generates interface and impls"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m => m.field("amount", 1, TypeRef.LONG) }
    }
    val module = runPipeline(schema, VersionContextPass)

    val ctxIface = module.collect { case i: InterfaceOp => i }.find(_.name == "VersionContext")
    assert(ctxIface.isDefined, "Should generate VersionContext interface")
    assert(ctxIface.get.methods.exists(_.name == "wrapMoney"))
    assert(ctxIface.get.methods.exists(_.name == "parseMoneyFromBytes"))
    assert(ctxIface.get.methods.exists(_.name == "getVersionId"))

    val v1Impl = module.collect { case c: ClassOp => c }.find(_.name == "VersionContextV1")
    val v2Impl = module.collect { case c: ClassOp => c }.find(_.name == "VersionContextV2")
    assert(v1Impl.isDefined, "Should generate VersionContextV1")
    assert(v2Impl.isDefined, "Should generate VersionContextV2")
    assert(v1Impl.get.methods.exists(_.name == "wrapMoney"))

  // ── Full Batch 2 pipeline ──────────────────────────────────────────────

  test("all Batch 2 passes compose"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }
    val module = runPipeline(schema,
      ConflictResolutionPass,
      HasMethodsPass,
      ProtoWrapperPass,
      CommonMethodsPass,
      VersionContextPass,
      ProtocolVersionsPass,
    )

    // Verify all generated types exist
    val interfaces = module.collect { case i: InterfaceOp => i }.map(_.name)
    val classes = module.collect { case c: ClassOp => c }.map(_.name)

    assert(interfaces.contains("Money"))
    assert(interfaces.contains("ProtoWrapper"))
    assert(interfaces.contains("VersionContext"))
    assert(classes.contains("AbstractMoney"))
    assert(classes.contains("MoneyV1"))
    assert(classes.contains("MoneyV2"))
    assert(classes.contains("VersionContextV1"))
    assert(classes.contains("VersionContextV2"))
    assert(classes.contains("ProtocolVersions"))
