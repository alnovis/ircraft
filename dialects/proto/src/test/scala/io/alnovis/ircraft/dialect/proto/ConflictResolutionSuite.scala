package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.proto.types.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class ConflictResolutionSuite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  private def lowerAndResolve(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): Module =
    val pipeline =
      Pipeline("test", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, ConflictResolutionPass)
    pipeline.run(Module("test", Vector(schema)), ctx).module

  test("INT_ENUM conflict adds getXxxEnum to interface"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Payment") { m =>
        m.field("type", 1, TypeRef.INT, conflictType = ConflictType.IntEnum)
      }
    }

    val module = lowerAndResolve(schema)
    val files  = module.topLevel.collect { case f: FileOp => f }
    val iface  = files.flatMap(_.types).collectFirst { case i: InterfaceOp => i }.get

    assert(iface.methods.exists(_.name == "getType"), "Should have getType")
    assert(iface.methods.exists(_.name == "getTypeEnum"), s"Should have getTypeEnum, got: ${iface.methods.map(_.name)}")

  test("STRING_BYTES conflict adds getXxxBytes to interface"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Data") { m =>
        m.field("content", 1, TypeRef.STRING, conflictType = ConflictType.StringBytes)
      }
    }

    val module = lowerAndResolve(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assert(iface.methods.exists(_.name == "getContentBytes"))

  test("PRIMITIVE_MESSAGE conflict adds getXxxMessage and supportsXxxMessage"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Order") { m =>
        m.field("total", 1, TypeRef.INT, conflictType = ConflictType.PrimitiveMessage)
      }
    }

    val module = lowerAndResolve(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assert(iface.methods.exists(_.name == "getTotalMessage"))
    assert(iface.methods.exists(_.name == "supportsTotalMessage"))

  test("INT_ENUM conflict adds extract and getter to abstract class"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Payment") { m =>
        m.field("type", 1, TypeRef.INT, conflictType = ConflictType.IntEnum)
      }
    }

    val module = lowerAndResolve(schema)
    val absCls = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case c: ClassOp if c.isAbstract => c }
      .get

    assert(absCls.methods.exists(_.name == "extractTypeEnum"), s"Methods: ${absCls.methods.map(_.name)}")
    assert(absCls.methods.exists(_.name == "getTypeEnum"))

  test("no conflict type produces no additional methods"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Simple") { m =>
        m.field("name", 1, TypeRef.STRING)
      }
    }

    val module = lowerAndResolve(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assertEquals(iface.methods.size, 1) // only getName
