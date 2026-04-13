package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

class IrSuite extends munit.FunSuite {

  test("construct a simple TypeDecl with fields") {
    val decl = Decl.typeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(
        Field("id", TypeExpr.LONG),
        Field("name", TypeExpr.STR),
        Field("email", TypeExpr.STR),
        Field("active", TypeExpr.BOOL)
      )
    )
    decl.unfix match {
      case TypeDeclF(name, kind, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "User")
        assertEquals(kind, TypeKind.Product)
        assertEquals(fields.size, 4)
        assertEquals(fields.head.name, "id")
        assertEquals(fields.head.fieldType, TypeExpr.LONG)
      case _ => fail("expected TypeDeclF")
    }
  }

  test("construct a Protocol with functions") {
    val decl = Decl.typeDecl(
      name = "Repository",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.LONG)), TypeExpr.Optional(TypeExpr.Named("User"))),
        Func("save", Vector(Param("entity", TypeExpr.Named("User"))), TypeExpr.VOID)
      )
    )
    decl.unfix match {
      case TypeDeclF(_, _, _, functions, _, _, _, _, _, _) =>
        assertEquals(functions.size, 2)
        assertEquals(functions.head.name, "findById")
      case _ => fail("expected TypeDeclF")
    }
  }

  test("construct EnumDecl with variants") {
    val decl = Decl.enumDecl(
      name = "Color",
      variants = Vector(
        EnumVariant("Red"),
        EnumVariant("Green"),
        EnumVariant("Blue")
      )
    )
    decl.unfix match {
      case EnumDeclF(_, variants, _, _, _, _, _) =>
        assertEquals(variants.size, 3)
      case _ => fail("expected EnumDeclF")
    }
  }

  test("construct valued EnumDecl") {
    val decl = Decl.enumDecl(
      name = "HttpStatus",
      variants = Vector(
        EnumVariant("OK", args = Vector(Expr.Lit("200", TypeExpr.INT))),
        EnumVariant("NOT_FOUND", args = Vector(Expr.Lit("404", TypeExpr.INT)))
      ),
      functions = Vector(
        Func("code", returnType = TypeExpr.INT)
      )
    )
    decl.unfix match {
      case EnumDeclF(_, variants, _, _, _, _, _) =>
        assertEquals(variants.head.args.size, 1)
      case _ => fail("expected EnumDeclF")
    }
  }

  test("construct Module with CompilationUnits") {
    val unit = CompilationUnit(
      namespace = "com.example.model",
      declarations = Vector(
        Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
        Decl.typeDecl("Order", TypeKind.Product, fields = Vector(Field("total", TypeExpr.DOUBLE)))
      )
    )
    val module = Module("test", Vector(unit))
    assertEquals(module.units.size, 1)
    assertEquals(module.units.head.declarations.size, 2)
  }

  test("nested TypeDecl") {
    val inner = Decl.typeDecl("Address", TypeKind.Product, fields = Vector(Field("street", TypeExpr.STR)))
    val outer = Decl.typeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(Field("name", TypeExpr.STR)),
      nested = Vector(inner)
    )
    outer.unfix match {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) =>
        assertEquals(nested.size, 1)
      case _ => fail("expected TypeDeclF")
    }
  }

  test("function with body") {
    val getter = Func(
      name = "getName",
      returnType = TypeExpr.STR,
      body = Some(
        Body.of(
          Stmt.Return(Some(Expr.Access(Expr.This, "name")))
        )
      )
    )
    assert(getter.body.isDefined)
    assertEquals(getter.body.get.stmts.size, 1)
  }

  test("Meta typed keys") {
    val presentIn    = Meta.Key[Vector[String]]("merge.presentIn")
    val conflictType = Meta.Key[String]("merge.conflictType")

    val meta = Meta.empty
      .set(presentIn, Vector("v1", "v2"))
      .set(conflictType, "INT_ENUM")

    assertEquals(meta.get(presentIn), Some(Vector("v1", "v2")))
    assertEquals(meta.get(conflictType), Some("INT_ENUM"))
    assert(meta.contains(presentIn))
  }

  test("TypeExpr composite types") {
    val listOfUsers   = TypeExpr.ListOf(TypeExpr.Named("User"))
    val mapOfIds      = TypeExpr.MapOf(TypeExpr.STR, TypeExpr.LONG)
    val optionalEmail = TypeExpr.Optional(TypeExpr.STR)
    assert(optionalEmail.isInstanceOf[TypeExpr.Optional])

    listOfUsers match {
      case TypeExpr.ListOf(TypeExpr.Named(fqn)) => assertEquals(fqn, "User")
      case _                                    => fail("pattern match failed")
    }

    mapOfIds match {
      case TypeExpr.MapOf(TypeExpr.Primitive.Str, TypeExpr.Primitive.Int64) => ()
      case _                                                                => fail("pattern match failed")
    }
  }

  test("TypeExpr function type") {
    val mapper = TypeExpr.FuncType(Vector(TypeExpr.STR), TypeExpr.INT)
    mapper match {
      case TypeExpr.FuncType(Vector(TypeExpr.Primitive.Str), TypeExpr.Primitive.Int32) => ()
      case _                                                                           => fail("pattern match failed")
    }
  }
}
