package io.alnovis.ircraft.core

import cats._
import cats.data._
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

class PipelineSuite extends munit.FunSuite {

  type F[A] = Id[A]

  // Outcome over Id for tests with warnings/errors
  type OF[A] = IorT[Id, NonEmptyChain[Diagnostic], A]

  private val emptyModule = Module.empty[Fix[SemanticF]]("test")

  private def moduleWith(decls: Fix[SemanticF]*): Module[Fix[SemanticF]] =
    Module("test", Vector(CompilationUnit("com.example", decls.toVector)))

  test("Pass.pure transforms module") {
    val addField = Pass.pure[F]("add-field") { module =>
      module.copy(units = module.units.map { unit =>
        unit.copy(declarations = unit.declarations.map { fix =>
          fix.unfix match {
            case td: TypeDeclF[Fix[SemanticF] @unchecked] =>
              Fix[SemanticF](td.copy(fields = td.fields :+ Field("added", TypeExpr.STR)))
            case other => fix
          }
        })
      })
    }

    val input  = moduleWith(Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))
    val result = Pipeline.run(addField, input)

    result.units.head.declarations.head.unfix match {
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) =>
        assertEquals(fields.size, 2)
        assertEquals(fields.last.name, "added")
      case _ => fail("expected TypeDeclF")
    }
  }

  test("Pipeline.of composes passes left to right") {
    val pass1    = Pass.pure[F]("p1")(m => m.copy(name = m.name + "-1"))
    val pass2    = Pass.pure[F]("p2")(m => m.copy(name = m.name + "-2"))
    val pipeline = Pipeline.of(pass1, pass2)
    val result   = Pipeline.run(pipeline, emptyModule)
    assertEquals(result.name, "test-1-2")
  }

  test("Pass.id returns module unchanged") {
    val result = Pipeline.run(Pass.id[F], emptyModule)
    assertEquals(result, emptyModule)
  }

  test("Pipeline.build filters disabled passes") {
    val p1 = Pass.pure[F]("p1")(m => m.copy(name = m.name + "-1"))
    val p2 = Pass.pure[F]("p2")(m => m.copy(name = m.name + "-2"))
    val p3 = Pass.pure[F]("p3")(m => m.copy(name = m.name + "-3"))

    val pipeline = Pipeline.build(
      Vector(
        (p1, true),
        (p2, false),
        (p3, true)
      )
    )
    val result = Pipeline.run(pipeline, emptyModule)
    assertEquals(result.name, "test-1-3")
  }

  test("Outcome.fail stops pipeline (fail-fast via IorT Left)") {
    val failPass = Pass[OF]("fail") { _ =>
      Outcome.fail("fatal")
    }
    val neverRun = Pass.pure[OF]("never") { m =>
      m.copy(name = "should-not-reach")
    }
    val pipeline = Pipeline.of(failPass, neverRun)
    Pipeline.run(pipeline, emptyModule).value match {
      case Ior.Left(errors) =>
        assert(errors.exists(_.message == "fatal"))
      case other => fail(s"expected Ior.Left, got $other")
    }
  }

  test("Outcome.warn continues pipeline and accumulates") {
    val warnPass = Pass[OF]("warn") { module =>
      Outcome.warn("something fishy", module)
    }
    val addSuffix = Pass.pure[OF]("suffix") { m =>
      m.copy(name = m.name + "-done")
    }
    val pipeline = Pipeline.of(warnPass, addSuffix)
    Pipeline.run(pipeline, emptyModule).value match {
      case Ior.Both(warnings, result) =>
        assert(warnings.exists(_.isWarning))
        assertEquals(result.name, "test-done")
      case other => fail(s"expected Ior.Both, got $other")
    }
  }

  test("Lowering.pure creates module from source") {
    case class SqlTable(name: String, columns: Vector[String])

    val lowering: Lowering[F, Vector[SqlTable]] = Lowering.pure { tables =>
      val units = tables.map { t =>
        CompilationUnit(
          "com.example.model",
          Vector(
            Decl.typeDecl(
              name = t.name,
              kind = TypeKind.Product,
              fields = t.columns.map(c => Field(c, TypeExpr.STR))
            )
          )
        )
      }
      Module("sql", units)
    }

    val tables = Vector(SqlTable("Users", Vector("id", "name", "email")))
    val module = lowering(tables)
    module.units.head.declarations.head.unfix match {
      case TypeDeclF(name, _, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "Users")
        assertEquals(fields.size, 3)
      case _ => fail("expected TypeDeclF")
    }
  }

  test("Passes.validateResolved detects unresolved types via Outcome") {
    val module = moduleWith(
      Decl.typeDecl(
        "Order",
        TypeKind.Product,
        fields = Vector(Field("address", TypeExpr.Unresolved("com.example.Address")))
      )
    )
    Pipeline.run(Passes.validateResolved[Id], module).value match {
      case Ior.Left(errors) =>
        assert(errors.exists(d => d.isError && d.message.contains("Unresolved")))
      case other => fail(s"expected Ior.Left, got $other")
    }
  }

  test("Passes.validateResolved passes clean module") {
    val module = moduleWith(
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("name", TypeExpr.STR)))
    )
    Pipeline.run(Passes.validateResolved[Id], module).value match {
      case Ior.Right(m) => assertEquals(m.units.size, 1)
      case other        => fail(s"expected Ior.Right, got $other")
    }
  }
}
