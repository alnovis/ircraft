package io.alnovis.ircraft.core

import cats._
import cats.data.{ Ior, IorT, NonEmptyChain, NonEmptyVector }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.core.merge._

class MergeSuite extends munit.FunSuite {

  type F[A] = Id[A]

  private def module(version: String, decls: Fix[SemanticF]*): (String, Module[Fix[SemanticF]]) =
    (version, Module(version, Vector(CompilationUnit("com.example", decls.toVector))))

  private val pickFirst: MergeStrategy[F] = new MergeStrategy[F] {
    def onConflict(conflict: Conflict): IorT[F, NonEmptyChain[Diagnostic], Resolution] =
      Outcome.ok(Resolution.UseType(conflict.versions.head._2))
  }

  private val dualAccessor: MergeStrategy[F] = new MergeStrategy[F] {
    def onConflict(conflict: Conflict): IorT[F, NonEmptyChain[Diagnostic], Resolution] = {
      val types = conflict.versions.toVector.toMap
      Outcome.ok(Resolution.DualAccessor(types))
    }
  }

  private val skipConflicts: MergeStrategy[F] = new MergeStrategy[F] {
    def onConflict(conflict: Conflict): IorT[F, NonEmptyChain[Diagnostic], Resolution] =
      Outcome.ok(Resolution.Skip)
  }

  /** Extract Right or Both result, fail on Left. */
  private def unwrap(outcome: IorT[F, NonEmptyChain[Diagnostic], Module[Fix[SemanticF]]]): Module[Fix[SemanticF]] =
    outcome.value match {
      case Ior.Right(m)   => m
      case Ior.Both(_, m) => m
      case Ior.Left(errs) => fail(s"unexpected errors: ${errs.map(_.message).toList.mkString(", ")}")
    }

  private def extractTypeDecl(fix: Fix[SemanticF]): TypeDeclF[Fix[SemanticF]] =
    fix.unfix match {
      case td: TypeDeclF[Fix[SemanticF] @unchecked] => td
      case other => fail(s"expected TypeDeclF, got $other")
    }

  private def extractEnumDecl(fix: Fix[SemanticF]): EnumDeclF[Fix[SemanticF]] =
    fix.unfix match {
      case ed: EnumDeclF[Fix[SemanticF] @unchecked] => ed
      case other => fail(s"expected EnumDeclF, got $other")
    }

  test("merge identical types from 2 versions") {
    val v1 = module("v1", Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))
    val v2 = module("v2", Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))

    val merged = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    assertEquals(merged.name, "v1+v2")
    assertEquals(merged.units.size, 1)

    val td = extractTypeDecl(merged.units.head.declarations.head)
    assertEquals(td.name, "User")
    assertEquals(td.fields.size, 1)
    assertEquals(td.meta.get(Merge.Keys.presentIn), Some(Vector("v1", "v2")))
  }

  test("merge unions fields from different versions") {
    val v1 = module(
      "v1",
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR)))
    )
    val v2 = module(
      "v2",
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG), Field("email", TypeExpr.STR)))
    )

    val merged     = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    val td         = extractTypeDecl(merged.units.head.declarations.head)
    val fieldNames = td.fields.map(_.name)
    assert(fieldNames.contains("id"))
    assert(fieldNames.contains("name"))
    assert(fieldNames.contains("email"))
  }

  test("merge with function return type conflict -- pickFirst resolves") {
    val v1 = module(
      "v1",
      Decl.typeDecl("Api", TypeKind.Protocol, functions = Vector(Func("getStatus", returnType = TypeExpr.INT)))
    )
    val v2 = module(
      "v2",
      Decl.typeDecl("Api", TypeKind.Protocol, functions = Vector(Func("getStatus", returnType = TypeExpr.STR)))
    )

    val merged = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    val td     = extractTypeDecl(merged.units.head.declarations.head)
    val func   = td.functions.find(_.name == "getStatus").get
    assertEquals(func.returnType, TypeExpr.INT)
    assertEquals(func.meta.get(Merge.Keys.conflictType), Some("RESOLVED"))
  }

  test("merge with function conflict -- dualAccessor resolution") {
    val v1 = module(
      "v1",
      Decl.typeDecl("Api", TypeKind.Protocol, functions = Vector(Func("getStatus", returnType = TypeExpr.INT)))
    )
    val v2 = module(
      "v2",
      Decl.typeDecl("Api", TypeKind.Protocol, functions = Vector(Func("getStatus", returnType = TypeExpr.STR)))
    )

    val merged = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), dualAccessor))
    val td     = extractTypeDecl(merged.units.head.declarations.head)
    val func   = td.functions.find(_.name == "getStatus").get
    assertEquals(func.meta.get(Merge.Keys.conflictType), Some("DUAL_ACCESSOR"))
    assert(func.meta.get(Merge.Keys.typePerVersion).isDefined)
  }

  test("merge with conflict -- skip resolution removes function") {
    val v1 = module(
      "v1",
      Decl.typeDecl(
        "Api",
        TypeKind.Protocol,
        functions = Vector(
          Func("getStatus", returnType = TypeExpr.INT),
          Func("getName", returnType = TypeExpr.STR)
        )
      )
    )
    val v2 = module(
      "v2",
      Decl.typeDecl(
        "Api",
        TypeKind.Protocol,
        functions = Vector(
          Func("getStatus", returnType = TypeExpr.STR),
          Func("getName", returnType = TypeExpr.STR)
        )
      )
    )

    val merged    = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), skipConflicts))
    val td        = extractTypeDecl(merged.units.head.declarations.head)
    val funcNames = td.functions.map(_.name)
    assert(!funcNames.contains("getStatus"))
    assert(funcNames.contains("getName"))
  }

  test("merge type present in only one version") {
    val v1 = module(
      "v1",
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
      Decl.typeDecl("Admin", TypeKind.Product, fields = Vector(Field("role", TypeExpr.STR)))
    )
    val v2 = module("v2", Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))

    val merged    = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    val declNames = merged.units.head.declarations.map(d => SemanticF.name(d.unfix))
    assert(declNames.contains("User"))
    assert(declNames.contains("Admin"))

    val admin = merged.units.head.declarations
      .find(d => SemanticF.name(d.unfix) == "Admin")
      .map(d => extractTypeDecl(d))
      .get
    assertEquals(admin.meta.get(Merge.Keys.presentIn), Some(Vector("v1")))
  }

  test("merge enum unions variants") {
    val v1 = module("v1", Decl.enumDecl("Status", variants = Vector(EnumVariant("ACTIVE"), EnumVariant("INACTIVE"))))
    val v2 = module("v2", Decl.enumDecl("Status", variants = Vector(EnumVariant("ACTIVE"), EnumVariant("DELETED"))))

    val merged       = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    val ed           = extractEnumDecl(merged.units.head.declarations.head)
    val variantNames = ed.variants.map(_.name)
    assertEquals(variantNames.toSet, Set("ACTIVE", "INACTIVE", "DELETED"))
  }

  test("merge 3 versions") {
    val v1 =
      module("v1", Decl.typeDecl("Api", TypeKind.Protocol, functions = Vector(Func("getA", returnType = TypeExpr.STR))))
    val v2 = module(
      "v2",
      Decl.typeDecl(
        "Api",
        TypeKind.Protocol,
        functions = Vector(Func("getA", returnType = TypeExpr.STR), Func("getB", returnType = TypeExpr.INT))
      )
    )
    val v3 = module(
      "v3",
      Decl.typeDecl(
        "Api",
        TypeKind.Protocol,
        functions = Vector(
          Func("getA", returnType = TypeExpr.STR),
          Func("getB", returnType = TypeExpr.INT),
          Func("getC", returnType = TypeExpr.BOOL)
        )
      )
    )

    val merged    = unwrap(Merge.merge(NonEmptyVector.of(v1, v2, v3), pickFirst))
    val td        = extractTypeDecl(merged.units.head.declarations.head)
    val funcNames = td.functions.map(_.name).toSet
    assertEquals(funcNames, Set("getA", "getB", "getC"))
  }

  test("merge sets sources on module meta") {
    val v1 = module("v1", Decl.typeDecl("X", TypeKind.Product))
    val v2 = module("v2", Decl.typeDecl("X", TypeKind.Product))

    val merged = unwrap(Merge.merge(NonEmptyVector.of(v1, v2), pickFirst))
    assertEquals(merged.meta.get(Merge.Keys.sources), Some(Vector("v1", "v2")))
  }

  test("merge mixed kinds produces warning via Ior.Both") {
    val v1 = module("v1", Decl.typeDecl("X", TypeKind.Product))
    val v2 = module("v2", Decl.enumDecl("X", variants = Vector(EnumVariant("A"))))

    Merge.merge(NonEmptyVector.of(v1, v2), pickFirst).value match {
      case Ior.Both(warnings, _) =>
        assert(warnings.exists(d => d.isWarning && d.message.contains("Mixed")))
      case other => fail(s"expected Ior.Both with warnings, got $other")
    }
  }
}
