package io.alnovis.ircraft.pipeline.prototojava

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.types.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class EndToEndSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig(
    apiPackage = "com.example.api",
    implPackagePattern = "com.example.%s"
  )

  val pipeline: ProtoToJavaPipeline = ProtoToJavaPipeline(config)

  test("end-to-end: simple message → Java source files"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val result = pipeline.execute(Module("test", Vector(schema)))
    assert(result.isRight, s"Pipeline failed: ${result.left.getOrElse(Nil)}")

    val files = result.toOption.get
    assert(files.nonEmpty, "Should produce files")

    // Check we have the expected files
    val paths = files.keySet
    assert(paths.exists(_.contains("Money.java")), s"Expected Money.java, got: $paths")
    assert(paths.exists(_.contains("AbstractMoney.java")), s"Expected AbstractMoney.java, got: $paths")
    assert(paths.exists(_.contains("MoneyV1.java")), s"Expected MoneyV1.java, got: $paths")
    assert(paths.exists(_.contains("MoneyV2.java")), s"Expected MoneyV2.java, got: $paths")

  test("generated interface contains correct Java code"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val result      = pipeline.execute(Module("test", Vector(schema)))
    val files       = result.toOption.get
    val ifaceSource = files.find(_._1.endsWith("Money.java")).get._2

    assert(ifaceSource.contains("package com.example.api;"), s"Missing package:\n$ifaceSource")
    assert(ifaceSource.contains("public interface Money"), s"Missing interface decl:\n$ifaceSource")
    assert(ifaceSource.contains("long getAmount()"), s"Missing getAmount:\n$ifaceSource")
    assert(ifaceSource.contains("String getCurrency()"), s"Missing getCurrency:\n$ifaceSource")

  test("generated abstract class has template methods"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.field("total", 2, TypeRef.DOUBLE)
      }
    }

    val result         = pipeline.execute(Module("test", Vector(schema)))
    val files          = result.toOption.get
    val abstractSource = files.find(_._1.endsWith("AbstractOrder.java")).get._2

    assert(abstractSource.contains("public abstract class AbstractOrder"), s"Missing abstract class:\n$abstractSource")
    assert(abstractSource.contains("protected abstract long extractId()"), s"Missing extractId:\n$abstractSource")
    assert(
      abstractSource.contains("protected abstract double extractTotal()"),
      s"Missing extractTotal:\n$abstractSource"
    )
    assert(abstractSource.contains("public long getId()"), s"Missing getId:\n$abstractSource")

  test("generated impl class overrides extract methods"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Foo") { m =>
        m.field("bar", 1, TypeRef.STRING)
      }
    }

    val result     = pipeline.execute(Module("test", Vector(schema)))
    val files      = result.toOption.get
    val implSource = files.find(_._1.endsWith("FooV1.java")).get._2

    assert(implSource.contains("package com.example.v1;"), s"Wrong package:\n$implSource")
    assert(implSource.contains("public class FooV1"), s"Missing class:\n$implSource")
    assert(implSource.contains("protected String extractBar()"), s"Missing extractBar:\n$implSource")

  test("generated enum has constants and getValue"):
    val schema = ProtoSchema.build("v1") { s =>
      s.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
        e.value("DELETED", 2)
      }
    }

    val result     = pipeline.execute(Module("test", Vector(schema)))
    val files      = result.toOption.get
    val enumSource = files.find(_._1.endsWith("Status.java")).get._2

    assert(enumSource.contains("public enum Status"), s"Missing enum:\n$enumSource")
    assert(enumSource.contains("UNKNOWN(0)"), s"Missing UNKNOWN:\n$enumSource")
    assert(enumSource.contains("ACTIVE(1)"), s"Missing ACTIVE:\n$enumSource")
    assert(enumSource.contains("DELETED(2)"), s"Missing DELETED:\n$enumSource")
    assert(enumSource.contains("int getValue()"), s"Missing getValue:\n$enumSource")

  test("message with oneof generates case enum"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Event") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.oneof("payload") { o =>
          o.field("text", 2, TypeRef.STRING)
          o.field("number", 3, TypeRef.INT)
        }
      }
    }

    val result      = pipeline.execute(Module("test", Vector(schema)))
    val files       = result.toOption.get
    val ifaceSource = files.find(_._1.endsWith("Event.java")).get._2

    assert(ifaceSource.contains("PayloadCase"), s"Missing PayloadCase:\n$ifaceSource")
    assert(ifaceSource.contains("getPayloadCase()"), s"Missing getPayloadCase:\n$ifaceSource")

  test("version-specific fields only in relevant impl"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Item") { m =>
        m.field("name", 1, TypeRef.STRING)
        m.field("description", 2, TypeRef.STRING, presentIn = Set("v2"))
      }
    }

    val result   = pipeline.execute(Module("test", Vector(schema)))
    val files    = result.toOption.get
    val v1Source = files.find(_._1.endsWith("ItemV1.java")).get._2
    val v2Source = files.find(_._1.endsWith("ItemV2.java")).get._2

    assert(!v1Source.contains("extractDescription"), s"V1 should not have extractDescription:\n$v1Source")
    assert(v2Source.contains("extractDescription"), s"V2 should have extractDescription:\n$v2Source")

  test("pipeline rejects invalid schema"):
    val schema = ProtoSchema.build()(_ => ()) // empty versions
    val result = pipeline.execute(Module("test", Vector(schema)))
    assert(result.isLeft, "Should fail for empty schema")

  test("DirectJavaEmitter used standalone with semantic IR"):
    val emitter = DirectJavaEmitter()
    val file = FileOp(
      "com.example",
      Vector(
        InterfaceOp(
          "Greeter",
          methods = Vector(MethodOp("greet", TypeRef.STRING, modifiers = Set(Modifier.Public, Modifier.Abstract)))
        )
      )
    )
    val module = Module("test", Vector(file))
    val result = emitter.emit(module)

    assertEquals(result.size, 1)
    val source = result.values.head
    assert(source.contains("package com.example;"))
    assert(source.contains("public interface Greeter"))
    assert(source.contains("String greet()"))
