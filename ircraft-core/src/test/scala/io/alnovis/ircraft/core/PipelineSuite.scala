package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*

class PipelineSuite extends munit.FunSuite:

  // use Id as the simplest monad -- no IO needed for pure tests
  type F[A] = Id[A]

  private val emptyModule = Module.empty("test")

  private def moduleWith(decls: Decl*): Module =
    Module("test", Vector(CompilationUnit("com.example", decls.toVector)))

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

    assert(diags.isEmpty)
    val fields = result.units.head.declarations.head.asInstanceOf[Decl.TypeDecl].fields
    assertEquals(fields.size, 2)
    assertEquals(fields.last.name, "added")

  test("Pipeline.of composes passes left to right"):
    val pass1 = Pass.pure[F]("p1") { m =>
      m.copy(name = m.name + "-1")
    }
    val pass2 = Pass.pure[F]("p2") { m =>
      m.copy(name = m.name + "-2")
    }
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
    assertEquals(diags.length, 2L)
    assert(diags.exists(_.isWarning))

  test("composed passes accumulate diagnostics from all passes"):
    val p1 = Pass[F]("p1") { m =>
      Pipe.warn[F]("warn from p1").as(m)
    }
    val p2 = Pass[F]("p2") { m =>
      Pipe.error[F]("error from p2").as(m)
    }
    val pipeline = Pipeline.of(p1, p2)
    val (diags, _) = Pipeline.run(pipeline, emptyModule)

    assertEquals(diags.length, 2L)
    assert(diags.exists(d => d.isWarning && d.message == "warn from p1"))
    assert(diags.exists(d => d.isError && d.message == "error from p2"))

  test("Pass.id returns module unchanged"):
    val (diags, result) = Pipeline.run(Pass.id[F], emptyModule)
    assert(diags.isEmpty)
    assertEquals(result, emptyModule)

  test("Pass.withDiag creates pass with diagnostics tuple"):
    val pass = Pass.withDiag[F]("check") { module =>
      val warnings = module.units.flatMap { unit =>
        unit.declarations.collect {
          case Decl.TypeDecl(name, _, fields, _, _, _, _, _, _, _) if fields.isEmpty =>
            Diagnostic(Severity.Warning, s"Type $name has no fields")
        }
      }
      (Chain.fromSeq(warnings), module)
    }

    val input = moduleWith(
      Decl.TypeDecl("Empty", TypeKind.Product),
      Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG)))
    )
    val (diags, _) = Pipeline.run(pass, input)
    assertEquals(diags.length, 1L)
    assert(diags.headOption.getOrElse(fail("empty")).message.contains("Empty"))

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
