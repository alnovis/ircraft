package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.GenericProtoToCodePipeline
import io.alnovis.ircraft.dialect.semantic.ops.*

class GenericPipelineSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig(
    apiPackage = "com.example.api",
    implPackagePattern = "com.example.%s"
  )

  // Use a no-op emitter -- we test IR, not generated code
  private val noopEmitter = new io.alnovis.ircraft.core.emit.Emitter:
    def emit(module: IrModule): Map[String, String] = Map.empty

  val generic: GenericProtoToCodePipeline = GenericProtoToCodePipeline(config, noopEmitter)

  private def runPipeline(schema: Operation): PipelineResult =
    generic.pipeline.run(IrModule("test", Vector(schema)), PassContext())

  test("generic pipeline produces interface, abstract class, and impl classes"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }
    val result = runPipeline(schema)
    assert(result.isSuccess)

    val interfaces = result.module.collect { case i: InterfaceOp => i }
    val classes    = result.module.collect { case c: ClassOp => c }

    assert(interfaces.exists(_.name == "Money"), "Should have Money interface")
    assert(classes.exists(_.name == "AbstractMoney"), "Should have AbstractMoney")
    assert(classes.exists(_.name == "MoneyV1"), "Should have MoneyV1")
    assert(classes.exists(_.name == "MoneyV2"), "Should have MoneyV2")

  test("generic pipeline does NOT produce ProtoWrapper"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val result = runPipeline(schema)
    assert(result.isSuccess)

    val interfaces = result.module.collect { case i: InterfaceOp => i }
    assert(!interfaces.exists(_.name == "ProtoWrapper"), "Should NOT have ProtoWrapper")

  test("generic pipeline does NOT produce VersionContext"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val result = runPipeline(schema)
    assert(result.isSuccess)

    val interfaces2 = result.module.collect { case i: InterfaceOp => i }
    val classes2    = result.module.collect { case c: ClassOp => c }
    assert(!interfaces2.exists(_.name.contains("VersionContext")), "Should NOT have VersionContext interface")
    assert(!classes2.exists(_.name.contains("VersionContext")), "Should NOT have VersionContext class")

  test("generic pipeline does NOT produce ProtocolVersions"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val result = runPipeline(schema)
    assert(result.isSuccess)

    val classes = result.module.collect { case c: ClassOp => c }
    assert(!classes.exists(_.name.contains("ProtocolVersions")), "Should NOT have ProtocolVersions")

  test("generic pipeline rejects invalid schema"):
    val schema = ProtoSchema.build()(_ => ())
    val result = runPipeline(schema)
    assert(result.hasErrors)
