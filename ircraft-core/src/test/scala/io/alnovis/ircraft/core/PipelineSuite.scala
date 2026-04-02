package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*

class PipelineSuite extends munit.FunSuite:

  type F[A] = Id[A]

  private val emptyModule = Module.empty("test")

  private def moduleWith(decls: Decl*): Module =
    Module("test", Vector(CompilationUnit("com.example", decls.toVector)))

  /** Filter out Info-level diagnostics (pass tracing) for assertions. */
  private def issues(diags: Chain[Diagnostic]): Chain[Diagnostic] =
    diags.filter(d => d.isError || d.isWarning)

  test("Pass.pure transforms module"):
    val addField = Pass.pure[F]("add-field") { module =>
      module.copy(units = module.units.map { unit =>
        unit.copy(declarations = unit.declarations.map {
          case td: Decl.TypeDecl =>
            td.copy(fields = td.fields :+ Field("added", TypeExpr.STR))
          case other => other
        })
      })
    }

    val input = moduleWith(Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))
    val (diags, result) = Pipeline.run(addField, input)

    assert(issues(diags).isEmpty)
    val fields = result.units.head.declarations.head.asInstanceOf[Decl.TypeDecl].fields
    assertEquals(fields.size, 2)
    assertEquals(fields.last.name, "added")

  test("Pipeline.of composes passes left to right"):
    val pass1 = Pass.pure[F]("p1")(m => m.copy(name = m.name + "-1"))
    val pass2 = Pass.pure[F]("p2")(m => m.copy(name = m.name + "-2"))
    val pipeline = Pipeline.of(pass1, pass2)
    val (_, result) = Pipeline.run(pipeline, emptyModule)
    assertEquals(result.name, "test-1-2")

  test("Pass accumulates diagnostics via WriterT"):
    val warnPass = Pass[F]("warn") { module =>
      for
        _ <- Pipe.warn[F]("something fishy")
        _ <- Pipe.info[F]("just info")
      yield module
    }

    val (diags, _) = Pipeline.run(warnPass, emptyModule)
    // 1 info from pass tracing + 1 warn + 1 info from user = 3 total
    assert(diags.exists(_.isWarning))
    assertEquals(issues(diags).length, 1L) // only the warning is an "issue"

  test("composed passes accumulate diagnostics from all passes"):
    val p1 = Pass[F]("p1") { m =>
      Pipe.warn[F]("warn from p1").as(m)
    }
    val p2 = Pass[F]("p2") { m =>
      Pipe.error[F]("error from p2").as(m)
    }
    val pipeline = Pipeline.of(p1, p2)
    val (diags, _) = Pipeline.run(pipeline, emptyModule)

    val real = issues(diags)
    assert(real.exists(d => d.isWarning && d.message == "warn from p1"))
    // p2 not run because p1 has no errors, but fail-fast doesn't trigger on warnings
    assert(real.exists(d => d.isError && d.message == "error from p2"))

  test("Pass.id returns module unchanged"):
    val (diags, result) = Pipeline.run(Pass.id[F], emptyModule)
    assert(diags.isEmpty) // id doesn't add tracing
    assertEquals(result, emptyModule)

  test("Pass.withDiag creates pass with diagnostics tuple"):
    val pass = Pass.withDiag[F]("check") { module =>
      val warnings = module.units.flatMap { unit =>
        unit.declarations.collect {
          case td: Decl.TypeDecl if td.fields.isEmpty =>
            Diagnostic(Severity.Warning, s"Type ${td.name} has no fields")
        }
      }
      (Chain.fromSeq(warnings), module)
    }

    val input = moduleWith(
      Decl.TypeDecl("Empty", TypeKind.Product),
      Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG)))
    )
    val (diags, _) = Pipeline.run(pass, input)
    val warns = issues(diags)
    assertEquals(warns.length, 1L)
    assert(warns.headOption.getOrElse(fail("empty")).message.contains("Empty"))

  test("Pipeline.build filters disabled passes"):
    val p1 = Pass.pure[F]("p1")(m => m.copy(name = m.name + "-1"))
    val p2 = Pass.pure[F]("p2")(m => m.copy(name = m.name + "-2"))
    val p3 = Pass.pure[F]("p3")(m => m.copy(name = m.name + "-3"))

    val pipeline = Pipeline.build(Vector(
      (p1, true),
      (p2, false),
      (p3, true),
    ))
    val (_, result) = Pipeline.run(pipeline, emptyModule)
    assertEquals(result.name, "test-1-3")

  test("error in pass stops pipeline (fail-fast)"):
    val failPass = Pass[F]("fail") { m =>
      Pipe.error[F]("fatal").as(m)
    }
    val neverRun = Pass.pure[F]("never") { m =>
      m.copy(name = "should-not-reach")
    }
    val pipeline = Pipeline.of(failPass, neverRun)
    val (diags, result) = Pipeline.run(pipeline, emptyModule)
    assert(diags.exists(_.isError))
    assertEquals(result.name, "test") // neverRun was skipped

  test("Lowering.pure creates module from source"):
    case class SqlTable(name: String, columns: Vector[String])

    val lowering: Lowering[F, Vector[SqlTable]] = Lowering.pure { tables =>
      val units = tables.map { t =>
        CompilationUnit(
          "com.example.model",
          Vector(Decl.TypeDecl(
            name = t.name,
            kind = TypeKind.Product,
            fields = t.columns.map(c => Field(c, TypeExpr.STR))
          ))
        )
      }
      Module("sql", units)
    }

    val tables = Vector(SqlTable("Users", Vector("id", "name", "email")))
    val (diags, module) = Pipe.run(lowering(tables))
    assert(diags.isEmpty)
    assertEquals(module.units.head.declarations.head.asInstanceOf[Decl.TypeDecl].name, "Users")
    assertEquals(module.units.head.declarations.head.asInstanceOf[Decl.TypeDecl].fields.size, 3)

  test("Passes.validateResolved detects unresolved types"):
    val module = moduleWith(
      Decl.TypeDecl("Order", TypeKind.Product,
        fields = Vector(Field("address", TypeExpr.Unresolved("com.example.Address"))))
    )
    val (diags, _) = Pipeline.run(Passes.validateResolved[F], module)
    assert(issues(diags).exists(d => d.isError && d.message.contains("Unresolved")))
