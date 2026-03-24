package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class NestedMessageSuite extends munit.FunSuite:

  val config: LoweringConfig            = LoweringConfig("com.example.api", "com.example.%s")
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  private def lower(schema: io.alnovis.ircraft.dialect.proto.ops.SchemaOp): Module =
    val pipeline = Pipeline("test", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering)
    pipeline.run(Module("test", Vector(schema)), ctx).module

  test("nested message generates nested interface"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.nestedMessage("Item") { n =>
          n.field("name", 1, TypeRef.STRING)
          n.field("quantity", 2, TypeRef.INT)
        }
      }
    }
    val module     = lower(schema)
    val orderIface = module.collect { case i: InterfaceOp => i }.find(_.name == "Order").get

    val nestedIface = orderIface.nestedTypes.collectFirst { case i: InterfaceOp => i }
    assert(
      nestedIface.isDefined,
      s"Should have nested Item interface, got: ${orderIface.nestedTypes.map(_.getClass.getSimpleName)}"
    )
    assertEquals(nestedIface.get.name, "Item")
    assertEquals(nestedIface.get.methods.size, 2)

  test("nested message generates nested abstract class"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.nestedMessage("Item") { n =>
          n.field("name", 1, TypeRef.STRING)
        }
      }
    }
    val module = lower(schema)
    val abstractOrder = module
      .collect { case c: ClassOp => c }
      .find(c => c.isAbstract && c.name == "AbstractOrder")
      .get

    val nestedAbstract = abstractOrder.nestedTypes.collectFirst { case c: ClassOp if c.isAbstract => c }
    assert(
      nestedAbstract.isDefined,
      s"Should have nested AbstractItem, got: ${abstractOrder.nestedTypes.map(_.getClass.getSimpleName)}"
    )
    assertEquals(nestedAbstract.get.name, "AbstractItem")

  test("nested message generates nested impl class per version"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.nestedMessage("Item") { n =>
          n.field("name", 1, TypeRef.STRING)
        }
      }
    }
    val module = lower(schema)

    val orderV1  = module.collect { case c: ClassOp => c }.find(_.name == "OrderV1").get
    val nestedV1 = orderV1.nestedTypes.collectFirst { case c: ClassOp => c }
    assert(nestedV1.isDefined, "Should have nested ItemV1")
    assertEquals(nestedV1.get.name, "ItemV1")

    val orderV2  = module.collect { case c: ClassOp => c }.find(_.name == "OrderV2").get
    val nestedV2 = orderV2.nestedTypes.collectFirst { case c: ClassOp => c }
    assert(nestedV2.isDefined, "Should have nested ItemV2")
    assertEquals(nestedV2.get.name, "ItemV2")

  test("deeply nested messages work recursively"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Root") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.nestedMessage("Level1") { n1 =>
          n1.field("x", 1, TypeRef.INT)
          n1.nestedMessage("Level2") { n2 =>
            n2.field("y", 1, TypeRef.STRING)
          }
        }
      }
    }
    val module = lower(schema)

    val rootIface   = module.collect { case i: InterfaceOp => i }.find(_.name == "Root").get
    val level1Iface = rootIface.nestedTypes.collectFirst { case i: InterfaceOp => i }.get
    assertEquals(level1Iface.name, "Level1")
    val level2Iface = level1Iface.nestedTypes.collectFirst { case i: InterfaceOp => i }.get
    assertEquals(level2Iface.name, "Level2")

    val abstractRoot = module
      .collect { case c: ClassOp => c }
      .find(c => c.isAbstract && c.name == "AbstractRoot")
      .get
    val abstractL1 = abstractRoot.nestedTypes.collectFirst { case c: ClassOp if c.isAbstract => c }.get
    assertEquals(abstractL1.name, "AbstractLevel1")
    val abstractL2 = abstractL1.nestedTypes.collectFirst { case c: ClassOp if c.isAbstract => c }.get
    assertEquals(abstractL2.name, "AbstractLevel2")
