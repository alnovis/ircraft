package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class HasMethodsSuite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  private def lowerAndEnrich(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): IrModule =
    val pipeline = Pipeline("test", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering, HasMethodsPass)
    pipeline.run(IrModule("test", Vector(schema)), ctx).module

  test("optional field gets hasXxx in interface"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Person") { m =>
        m.field("name", 1, TypeRef.STRING, optional = true)
      }
    }

    val module = lowerAndEnrich(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assert(iface.methods.exists(_.name == "getName"))
    assert(iface.methods.exists(_.name == "hasName"), s"Methods: ${iface.methods.map(_.name)}")

  test("non-optional field does not get hasXxx"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Person") { m =>
        m.field("id", 1, TypeRef.LONG)
      }
    }

    val module = lowerAndEnrich(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assert(!iface.methods.exists(_.name == "hasId"))

  test("version-specific field gets supportsXxx in interface"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.field("notes", 2, TypeRef.STRING, presentIn = Set("v2"))
      }
    }

    val module = lowerAndEnrich(schema)
    val iface = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case i: InterfaceOp => i }
      .get

    assert(!iface.methods.exists(_.name == "supportsId"), "id is in all versions")
    assert(iface.methods.exists(_.name == "supportsNotes"), s"Methods: ${iface.methods.map(_.name)}")

  test("optional field gets extractHas and has in abstract class"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Person") { m =>
        m.field("name", 1, TypeRef.STRING, optional = true)
      }
    }

    val module = lowerAndEnrich(schema)
    val absCls = module.topLevel
      .collect { case f: FileOp => f }
      .flatMap(_.types)
      .collectFirst { case c: ClassOp if c.isAbstract => c }
      .get

    assert(absCls.methods.exists(_.name == "extractHasName"), s"Methods: ${absCls.methods.map(_.name)}")
    assert(absCls.methods.exists(_.name == "hasName"))
