package io.alnovis.ircraft.pipeline.prototokotlin

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig

class EndToEndSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig(
    apiPackage = "com.example.api",
    implPackagePattern = "com.example.%s"
  )

  val pipeline: ProtoToKotlinPipeline = ProtoToKotlinPipeline(config)

  test("end-to-end: simple message -> Kotlin source files"):
    val schema = ProtoSchema.build("v1", "v2") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val result = pipeline.execute(IrModule("test", Vector(schema)))
    assert(result.isRight, s"Pipeline failed: ${result.left.getOrElse(Nil)}")

    val files = result.toOption.get
    assert(files.nonEmpty, "Should produce files")

    val paths = files.keySet
    assert(paths.exists(_.contains("Money.kt")), s"Expected Money.kt, got: $paths")
    assert(paths.exists(_.contains("AbstractMoney.kt")), s"Expected AbstractMoney.kt, got: $paths")
    assert(paths.exists(_.contains("MoneyV1.kt")), s"Expected MoneyV1.kt, got: $paths")
    assert(paths.exists(_.contains("MoneyV2.kt")), s"Expected MoneyV2.kt, got: $paths")

  test("generated interface contains correct Kotlin code"):
    val schema = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
      }
    }

    val result      = pipeline.execute(IrModule("test", Vector(schema)))
    val files       = result.toOption.get
    val ifaceSource = files.find(f => f._1.endsWith("Money.kt") && f._2.contains("interface Money")).get._2

    assert(ifaceSource.contains("package com.example.api"), s"Missing package:\n$ifaceSource")
    assert(ifaceSource.contains("interface Money"), s"Missing interface decl:\n$ifaceSource")
    assert(ifaceSource.contains("Long"), s"Missing Long type:\n$ifaceSource")
    assert(ifaceSource.contains("String"), s"Missing String type:\n$ifaceSource")

  test("generated enum has Kotlin syntax"):
    val schema = ProtoSchema.build("v1") { s =>
      s.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
        e.value("DELETED", 2)
      }
    }

    val result     = pipeline.execute(IrModule("test", Vector(schema)))
    val files      = result.toOption.get
    val enumSource = files.find(_._1.endsWith("Status.kt")).get._2

    assert(enumSource.contains("enum class Status"), s"Missing enum class:\n$enumSource")
    assert(enumSource.contains("UNKNOWN(0)"), s"Missing UNKNOWN:\n$enumSource")
    assert(enumSource.contains("ACTIVE(1)"), s"Missing ACTIVE:\n$enumSource")

  test("pipeline rejects invalid schema"):
    val schema = ProtoSchema.build()(_ => ())
    val result = pipeline.execute(IrModule("test", Vector(schema)))
    assert(result.isLeft, "Should fail for empty schema")
