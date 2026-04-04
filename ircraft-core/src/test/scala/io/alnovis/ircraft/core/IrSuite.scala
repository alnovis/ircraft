package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.ir.*

class IrSuite extends munit.FunSuite:

  test("construct a simple TypeDecl with fields"):
    val decl = Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(
        Field("id", TypeExpr.LONG),
        Field("name", TypeExpr.STR),
        Field("email", TypeExpr.STR),
        Field("active", TypeExpr.BOOL)
      )
    )
    assertEquals(decl.name, "User")
    assertEquals(decl.kind, TypeKind.Product)
    assertEquals(decl.fields.size, 4)
    assertEquals(decl.fields.head.name, "id")
    assertEquals(decl.fields.head.fieldType, TypeExpr.LONG)

  test("construct a Protocol with functions"):
    val decl = Decl.TypeDecl(
      name = "Repository",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.LONG)), TypeExpr.Optional(TypeExpr.Named("User"))),
        Func("save", Vector(Param("entity", TypeExpr.Named("User"))), TypeExpr.VOID)
      )
    )
    assertEquals(decl.functions.size, 2)
    assertEquals(decl.functions.head.name, "findById")

  test("construct EnumDecl with variants"):
    val decl = Decl.EnumDecl(
      name = "Color",
      variants = Vector(
        EnumVariant("Red"),
        EnumVariant("Green"),
        EnumVariant("Blue")
      )
    )
    assertEquals(decl.variants.size, 3)

  test("construct valued EnumDecl"):
    val decl = Decl.EnumDecl(
      name = "HttpStatus",
      variants = Vector(
        EnumVariant("OK", args = Vector(Expr.Lit("200", TypeExpr.INT))),
        EnumVariant("NOT_FOUND", args = Vector(Expr.Lit("404", TypeExpr.INT)))
      ),
      functions = Vector(
        Func("code", returnType = TypeExpr.INT)
      )
    )
    assertEquals(decl.variants.head.args.size, 1)

  test("construct Module with CompilationUnits"):
    val unit = CompilationUnit(
      namespace = "com.example.model",
      declarations = Vector(
        Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
        Decl.TypeDecl("Order", TypeKind.Product, fields = Vector(Field("total", TypeExpr.DOUBLE)))
      )
    )
    val module = Module("test", Vector(unit))
    assertEquals(module.units.size, 1)
    assertEquals(module.units.head.declarations.size, 2)

  test("nested TypeDecl"):
    val inner = Decl.TypeDecl("Address", TypeKind.Product, fields = Vector(Field("street", TypeExpr.STR)))
    val outer = Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(Field("name", TypeExpr.STR)),
      nested = Vector(inner)
    )
    assertEquals(outer.nested.size, 1)

  test("function with body"):
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

  test("Meta typed keys"):
    val presentIn    = Meta.Key[Vector[String]]("merge.presentIn")
    val conflictType = Meta.Key[String]("merge.conflictType")

    val meta = Meta.empty
      .set(presentIn, Vector("v1", "v2"))
      .set(conflictType, "INT_ENUM")

    assertEquals(meta.get(presentIn), Some(Vector("v1", "v2")))
    assertEquals(meta.get(conflictType), Some("INT_ENUM"))
    assert(meta.contains(presentIn))

  test("TypeExpr composite types"):
    val listOfUsers   = TypeExpr.ListOf(TypeExpr.Named("User"))
    val mapOfIds      = TypeExpr.MapOf(TypeExpr.STR, TypeExpr.LONG)
    val optionalEmail = TypeExpr.Optional(TypeExpr.STR)
    assert(optionalEmail.isInstanceOf[TypeExpr.Optional])

    listOfUsers match
      case TypeExpr.ListOf(TypeExpr.Named(fqn)) => assertEquals(fqn, "User")
      case _                                    => fail("pattern match failed")

    mapOfIds match
      case TypeExpr.MapOf(TypeExpr.Primitive.Str, TypeExpr.Primitive.Int64) => ()
      case _                                                                => fail("pattern match failed")

  test("TypeExpr function type"):
    val mapper = TypeExpr.FuncType(Vector(TypeExpr.STR), TypeExpr.INT)
    mapper match
      case TypeExpr.FuncType(Vector(TypeExpr.Primitive.Str), TypeExpr.Primitive.Int32) => ()
      case _                                                                           => fail("pattern match failed")
