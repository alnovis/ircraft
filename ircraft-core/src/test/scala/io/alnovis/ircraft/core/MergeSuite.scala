package io.alnovis.ircraft.core

import cats.*
import cats.data.NonEmptyVector
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.core.merge.*

class MergeSuite extends munit.FunSuite:

  type F[A] = Id[A]

  private def module(version: String, decls: Decl*): (String, Module) =
    (version, Module(version, Vector(CompilationUnit("com.example", decls.toVector))))

  // strategy that picks first type on conflict
  private val pickFirst: MergeStrategy[F] = new MergeStrategy[F]:
    def onConflict(conflict: Conflict): Resolution =
      Resolution.UseType(conflict.versions.head._2)

  // strategy that creates dual accessor
  private val dualAccessor: MergeStrategy[F] = new MergeStrategy[F]:
    def onConflict(conflict: Conflict): Resolution =
      val types = conflict.versions.toVector.toMap
      Resolution.DualAccessor(types)

  // strategy that skips conflicts
  private val skipConflicts: MergeStrategy[F] = new MergeStrategy[F]:
    def onConflict(conflict: Conflict): Resolution =
      Resolution.Skip

  test("merge identical types from 2 versions"):
    val v1 = module("v1", Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))
    val v2 = module("v2", Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))))

    val (diags, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    assert(diags.isEmpty)
    assertEquals(merged.name, "v1+v2")
    assertEquals(merged.units.size, 1)

    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    assertEquals(td.name, "User")
    assertEquals(td.fields.size, 1)
    assertEquals(td.meta.get(Merge.Keys.presentIn), Some(Vector("v1", "v2")))

  test("merge unions fields from different versions"):
    val v1 = module("v1", Decl.TypeDecl("User", TypeKind.Product,
      fields = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR))))
    val v2 = module("v2", Decl.TypeDecl("User", TypeKind.Product,
      fields = Vector(Field("id", TypeExpr.LONG), Field("email", TypeExpr.STR))))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    val fieldNames = td.fields.map(_.name)
    assert(fieldNames.contains("id"))
    assert(fieldNames.contains("name"))
    assert(fieldNames.contains("email"))

  test("merge with function return type conflict -- pickFirst resolves"):
    val v1 = module("v1", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getStatus", returnType = TypeExpr.INT))))
    val v2 = module("v2", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getStatus", returnType = TypeExpr.STR))))

    val (diags, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    assert(diags.isEmpty)

    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    val func = td.functions.find(_.name == "getStatus").get
    // pickFirst strategy chose v1's type (INT)
    assertEquals(func.returnType, TypeExpr.INT)
    assertEquals(func.meta.get(Merge.Keys.conflictType), Some("RESOLVED"))

  test("merge with function conflict -- dualAccessor resolution"):
    val v1 = module("v1", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getStatus", returnType = TypeExpr.INT))))
    val v2 = module("v2", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getStatus", returnType = TypeExpr.STR))))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), dualAccessor)
    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    val func = td.functions.find(_.name == "getStatus").get
    assertEquals(func.meta.get(Merge.Keys.conflictType), Some("DUAL_ACCESSOR"))
    assert(func.meta.get(Merge.Keys.typePerVersion).isDefined)

  test("merge with conflict -- skip resolution removes function"):
    val v1 = module("v1", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(
        Func("getStatus", returnType = TypeExpr.INT),
        Func("getName", returnType = TypeExpr.STR),
      )))
    val v2 = module("v2", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(
        Func("getStatus", returnType = TypeExpr.STR),
        Func("getName", returnType = TypeExpr.STR),
      )))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), skipConflicts)
    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    // getStatus was skipped, getName was kept
    val funcNames = td.functions.map(_.name)
    assert(!funcNames.contains("getStatus"))
    assert(funcNames.contains("getName"))

  test("merge type present in only one version"):
    val v1 = module("v1",
      Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
      Decl.TypeDecl("Admin", TypeKind.Product, fields = Vector(Field("role", TypeExpr.STR))),
    )
    val v2 = module("v2",
      Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
    )

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    val declNames = merged.units.head.declarations.map {
      case td: Decl.TypeDecl => td.name
      case _ => "?"
    }
    assert(declNames.contains("User"))
    assert(declNames.contains("Admin"))

    // Admin only in v1
    val admin = merged.units.head.declarations.collect { case td: Decl.TypeDecl if td.name == "Admin" => td }.head
    assertEquals(admin.meta.get(Merge.Keys.presentIn), Some(Vector("v1")))

  test("merge enum unions variants"):
    val v1 = module("v1", Decl.EnumDecl("Status",
      variants = Vector(EnumVariant("ACTIVE"), EnumVariant("INACTIVE"))))
    val v2 = module("v2", Decl.EnumDecl("Status",
      variants = Vector(EnumVariant("ACTIVE"), EnumVariant("DELETED"))))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    val ed = merged.units.head.declarations.head.asInstanceOf[Decl.EnumDecl]
    val variantNames = ed.variants.map(_.name)
    assertEquals(variantNames.toSet, Set("ACTIVE", "INACTIVE", "DELETED"))

  test("merge 3 versions"):
    val v1 = module("v1", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getA", returnType = TypeExpr.STR))))
    val v2 = module("v2", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getA", returnType = TypeExpr.STR), Func("getB", returnType = TypeExpr.INT))))
    val v3 = module("v3", Decl.TypeDecl("Api", TypeKind.Protocol,
      functions = Vector(Func("getA", returnType = TypeExpr.STR), Func("getB", returnType = TypeExpr.INT), Func("getC", returnType = TypeExpr.BOOL))))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2, v3), pickFirst)
    val td = merged.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    val funcNames = td.functions.map(_.name).toSet
    assertEquals(funcNames, Set("getA", "getB", "getC"))

  test("merge sets sources on module meta"):
    val v1 = module("v1", Decl.TypeDecl("X", TypeKind.Product))
    val v2 = module("v2", Decl.TypeDecl("X", TypeKind.Product))

    val (_, merged) = Merge.merge(NonEmptyVector.of(v1, v2), pickFirst)
    assertEquals(merged.meta.get(Merge.Keys.sources), Some(Vector("v1", "v2")))
