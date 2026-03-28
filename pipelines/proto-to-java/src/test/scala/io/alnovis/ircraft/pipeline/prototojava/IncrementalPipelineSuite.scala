package io.alnovis.ircraft.pipeline.prototojava

import java.nio.file.{ Files, Path }

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.cache.IncrementalCacheIO
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.ProtoToCodePipeline

class IncrementalPipelineSuite extends munit.FunSuite:

  val config: LoweringConfig = LoweringConfig(
    apiPackage = "com.example.api",
    implPackagePattern = "com.example.%s"
  )

  val pipeline: ProtoToCodePipeline = ProtoToCodePipeline(config, DirectJavaEmitter())

  val tmpDir: FunFixture[Path] = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ircraft-incr-test"),
    teardown = dir => Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))
  )

  private def twoMessageSchema() = ProtoSchema.build("v1") { s =>
    s.message("Money") { m =>
      m.field("amount", 1, TypeRef.LONG)
      m.field("currency", 2, TypeRef.STRING)
    }
    s.message("Order") { m =>
      m.field("id", 1, TypeRef.LONG)
      m.field("total", 2, TypeRef.DOUBLE)
    }
  }

  // -- First run: full generation -------------------------------------------

  tmpDir.test("first run generates all files and saves cache") { dir =>
    val module = Module("test", Vector(twoMessageSchema()))
    val result = pipeline.executeIncremental(module, dir)

    assert(result.isRight, s"Pipeline failed: ${result.left.getOrElse(Nil)}")
    val files = result.toOption.get
    assert(files.nonEmpty, "First run should produce files")
    assert(files.keySet.exists(_.contains("Money.java")))
    assert(files.keySet.exists(_.contains("Order.java")))

    // Cache should exist
    val cache = IncrementalCacheIO.load(dir)
    assert(cache.isDefined, "Cache should be saved")
    assert(cache.get.entities.contains("Money"))
    assert(cache.get.entities.contains("Order"))
    assert(cache.get.globalFiles.nonEmpty, "Should have global files")
  }

  // -- Second run: nothing changed ------------------------------------------

  tmpDir.test("second run with same module returns empty map") { dir =>
    val module = Module("test", Vector(twoMessageSchema()))

    pipeline.executeIncremental(module, dir)
    val second = pipeline.executeIncremental(module, dir)

    assert(second.isRight)
    assertEquals(second.toOption.get, Map.empty[String, String])
  }

  // -- Third run: one message changed ---------------------------------------

  tmpDir.test("changed message returns its files and global files") { dir =>
    val module1 = Module("test", Vector(twoMessageSchema()))
    pipeline.executeIncremental(module1, dir)

    // Change Money: add a field
    val schema2 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
        m.field("precision", 3, TypeRef.INT)
      }
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.field("total", 2, TypeRef.DOUBLE)
      }
    }
    val result = pipeline.executeIncremental(Module("test", Vector(schema2)), dir)

    assert(result.isRight)
    val files = result.toOption.get
    assert(files.nonEmpty, "Should have changed files")

    // Money files should be present
    assert(files.keySet.exists(_.contains("Money.java")), "Changed Money should be in result")
    assert(files.keySet.exists(_.contains("AbstractMoney.java")), "Changed AbstractMoney should be in result")

    // Order files should NOT be present (unchanged)
    val orderOnlyFiles = files.keySet.filter(p => p.contains("Order") && !p.contains("ProtoWrapper"))
    assertEquals(orderOnlyFiles, Set.empty[String], "Unchanged Order files should not be in result")

    // Global files should be present (something changed)
    assert(
      files.keySet.exists(_.contains("ProtoWrapper.java")),
      s"Global ProtoWrapper should be in result, got: ${files.keySet}"
    )
  }

  // -- New entity added -----------------------------------------------------

  tmpDir.test("new message returns new files plus global files") { dir =>
    val schema1 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
    }
    pipeline.executeIncremental(Module("test", Vector(schema1)), dir)

    val schema2 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
      }
    }
    val result = pipeline.executeIncremental(Module("test", Vector(schema2)), dir)
    assert(result.isRight)
    val files = result.toOption.get
    assert(files.keySet.exists(_.contains("Order.java")), "New Order should be in result")
  }

  // -- Enum support ---------------------------------------------------------

  tmpDir.test("enum changes are tracked") { dir =>
    val schema1 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
      s.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
      }
    }
    pipeline.executeIncremental(Module("test", Vector(schema1)), dir)

    val schema2 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
      }
      s.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
        e.value("DELETED", 2)
      }
    }
    val result = pipeline.executeIncremental(Module("test", Vector(schema2)), dir)
    assert(result.isRight)
    val files = result.toOption.get
    assert(files.keySet.exists(_.contains("Status.java")), "Changed enum should be in result")
  }

  // -- Pipeline errors propagate --------------------------------------------

  tmpDir.test("pipeline errors propagate in incremental mode") { dir =>
    val schema = ProtoSchema.build()(_ => ())
    val result = pipeline.executeIncremental(Module("test", Vector(schema)), dir)
    assert(result.isLeft, "Should propagate errors")
  }

  // -- Cache updated correctly ----------------------------------------------

  tmpDir.test("cache is updated with new hashes after incremental run") { dir =>
    val module1 = Module("test", Vector(twoMessageSchema()))
    pipeline.executeIncremental(module1, dir)
    val cacheBefore     = IncrementalCacheIO.load(dir).get
    val moneyHashBefore = cacheBefore.entities("Money").contentHash

    val schema2 = ProtoSchema.build("v1") { s =>
      s.message("Money") { m =>
        m.field("amount", 1, TypeRef.LONG)
        m.field("currency", 2, TypeRef.STRING)
        m.field("precision", 3, TypeRef.INT)
      }
      s.message("Order") { m =>
        m.field("id", 1, TypeRef.LONG)
        m.field("total", 2, TypeRef.DOUBLE)
      }
    }
    pipeline.executeIncremental(Module("test", Vector(schema2)), dir)
    val cacheAfter = IncrementalCacheIO.load(dir).get

    assertNotEquals(cacheAfter.entities("Money").contentHash, moneyHashBefore)
    assertEquals(cacheAfter.entities("Order").contentHash, cacheBefore.entities("Order").contentHash)
  }
