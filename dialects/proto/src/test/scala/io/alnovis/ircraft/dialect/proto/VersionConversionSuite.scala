package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class VersionConversionSuite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  private def lowerWithConversion(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): Module =
    Pipeline("test", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, VersionConversionPass)
      .run(Module("test", Vector(schema)), ctx)
      .module

  test("interface gets asVersion and getFieldsInaccessibleInVersion"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = lowerWithConversion(schema)
    val iface  = module.collect { case i: InterfaceOp => i }.find(_.name == "Money").get

    assert(iface.methods.exists(_.name == "asVersion"), s"Methods: ${iface.methods.map(_.name)}")
    assert(iface.methods.exists(_.name == "getFieldsInaccessibleInVersion"))

  test("abstract class gets asVersion implementation"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    val module = lowerWithConversion(schema)
    val absCls = module.collect { case c: ClassOp => c }.find(c => c.isAbstract && c.name == "AbstractMoney").get

    val asVersion = absCls.methods.find(_.name == "asVersion")
    assert(asVersion.isDefined)
    assert(asVersion.get.body.isDefined, "asVersion should have a body")

    val inaccessible = absCls.methods.find(_.name == "getFieldsInaccessibleInVersion")
    assert(inaccessible.isDefined)
    assert(inaccessible.get.body.isEmpty, "getFieldsInaccessibleInVersion should be abstract")

  test("asVersion return type matches interface name"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
      }
    }
    val module    = lowerWithConversion(schema)
    val absCls    = module.collect { case c: ClassOp => c }.find(c => c.isAbstract && c.name == "AbstractOrder").get
    val asVersion = absCls.methods.find(_.name == "asVersion").get

    assertEquals(asVersion.returnType, TypeRef.NamedType("Order"))
