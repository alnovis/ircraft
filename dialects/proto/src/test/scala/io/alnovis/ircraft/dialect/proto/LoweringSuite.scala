package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.proto.types.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class LoweringSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig(
    apiPackage = "com.example.api",
    implPackagePattern = "com.example.%s"
  )
  val lowering: ProtoToSemanticLowering = ProtoToSemanticLowering(config)
  val ctx: PassContext                  = PassContext()

  test("simple message produces interface + abstract + impl per version"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val module = Module("test", Vector(schema))
    val result = lowering.run(module, ctx)
    assert(result.isSuccess)

    val files = result.module.topLevel.collect { case f: FileOp => f }

    // Should have: interface file + abstract file + v1 impl + v2 impl = 4
    assertEquals(files.size, 4, s"Expected 4 files, got ${files.size}: ${files.map(f => f.types.headOption.map(_.asInstanceOf[Operation].opName).getOrElse("?"))}")

    // Interface
    val ifaceFile = files.find(f => f.types.exists(_.isInstanceOf[InterfaceOp]))
    assert(ifaceFile.isDefined, "Should have an interface file")
    val iface = ifaceFile.get.types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.name, "Money")
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "getAmount")
    assertEquals(iface.methods(1).name, "getCurrency")

    // Abstract class
    val abstractFile = files.find(f =>
      f.types.exists {
        case c: ClassOp => c.isAbstract
        case _          => false
      }
    )
    assert(abstractFile.isDefined, "Should have an abstract class file")
    val absCls = abstractFile.get.types.head.asInstanceOf[ClassOp]
    assertEquals(absCls.name, "AbstractMoney")
    assert(absCls.isAbstract)
    // 2 extract methods + 2 getter impls = 4
    assertEquals(absCls.methods.size, 4)

    // Impl classes
    val implFiles = files.filter(f =>
      f.types.exists {
        case c: ClassOp => !c.isAbstract
        case _          => false
      }
    )
    assertEquals(implFiles.size, 2)

    val v1Impl = implFiles.flatMap(_.types).collect { case c: ClassOp => c }.find(_.name == "MoneyV1")
    val v2Impl = implFiles.flatMap(_.types).collect { case c: ClassOp => c }.find(_.name == "MoneyV2")
    assert(v1Impl.isDefined, "Should have MoneyV1")
    assert(v2Impl.isDefined, "Should have MoneyV2")

  test("enum produces EnumClassOp"):
    val schema = ProtoSchema.build("v1") { s =>
      s.enum_("Currency") { e =>
        e.value("USD", 0)
        e.value("EUR", 1)
        e.value("GBP", 2)
      }
    }

    val module = Module("test", Vector(schema))
    val result = lowering.run(module, ctx)
    assert(result.isSuccess)

    val files = result.module.topLevel.collect { case f: FileOp => f }
    assertEquals(files.size, 1)

    val enumCls = files.head.types.head.asInstanceOf[EnumClassOp]
    assertEquals(enumCls.name, "Currency")
    assertEquals(enumCls.constants.size, 3)
    assertEquals(enumCls.constants.map(_.name), Vector("USD", "EUR", "GBP"))

  test("message with oneof produces case enum"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Event") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.oneof("payload") { o =>
          o.field("text", 2, TypeRef.STRING)
          o.field("number", 3, TypeRef.INT)
        }
      }
    }

    val module = Module("test", Vector(schema))
    val result = lowering.run(module, ctx)
    assert(result.isSuccess)

    val files     = result.module.topLevel.collect { case f: FileOp => f }
    val ifaceFile = files.find(_.types.exists(_.isInstanceOf[InterfaceOp])).get
    val iface     = ifaceFile.types.head.asInstanceOf[InterfaceOp]

    // Should have getter for id + discriminator for payload case
    assert(iface.methods.exists(_.name == "getId"))
    assert(iface.methods.exists(_.name == "getPayloadCase"))

    // Should have nested PayloadCase enum
    val nestedEnums = iface.nestedTypes.collect { case e: EnumClassOp => e }
    assert(nestedEnums.exists(_.name == "PayloadCase"), s"Expected PayloadCase enum, got: ${nestedEnums.map(_.name)}")

  test("field present only in some versions"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.field("notes", 2, TypeRef.STRING, presentIn = Set("v2"))
      }
    }

    val module = Module("test", Vector(schema))
    val result = lowering.run(module, ctx)
    assert(result.isSuccess)

    val files  = result.module.topLevel.collect { case f: FileOp => f }
    val v1Impl = files.flatMap(_.types).collect { case c: ClassOp if c.name == "OrderV1" => c }.head
    val v2Impl = files.flatMap(_.types).collect { case c: ClassOp if c.name == "OrderV2" => c }.head

    // v1 should only have extractId (notes not present in v1)
    assertEquals(v1Impl.methods.size, 1, s"v1 methods: ${v1Impl.methods.map(_.name)}")
    assertEquals(v1Impl.methods.head.name, "extractId")

    // v2 should have both
    assertEquals(v2Impl.methods.size, 2, s"v2 methods: ${v2Impl.methods.map(_.name)}")

  test("lowering runs in pipeline"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("bar", 1, TypeRef.STRING)
      }
    }

    val pipeline = Pipeline("proto-to-semantic", io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass, lowering)

    val result = pipeline.run(Module("test", Vector(schema)), ctx)
    assert(result.isSuccess)

    val files = result.module.topLevel.collect { case f: FileOp => f }
    assert(files.nonEmpty, "Should produce FileOps")

  test("packages are set correctly"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("bar", 1, TypeRef.STRING)
      }
    }

    val module = Module("test", Vector(schema))
    val result = lowering.run(module, ctx)
    val files  = result.module.topLevel.collect { case f: FileOp => f }

    val packages = files.map(_.packageName).toSet
    assert(packages.contains("com.example.api"), s"Expected api package, got: $packages")
    assert(packages.contains("com.example.api.impl"), s"Expected impl package, got: $packages")
    assert(packages.contains("com.example.v1"), s"Expected v1 package, got: $packages")
