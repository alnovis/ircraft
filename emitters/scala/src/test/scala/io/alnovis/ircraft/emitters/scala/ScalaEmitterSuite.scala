package io.alnovis.ircraft.emitters.scala

import cats.*
import io.alnovis.ircraft.core.ir.*

class ScalaEmitterSuite extends munit.FunSuite:

  type F[A] = Id[A]
  private val scala3Emitter = ScalaEmitter.scala3[F]
  private val scala2Emitter = ScalaEmitter.scala2[F]

  private def emit3(namespace: String, decls: Decl*): Map[java.nio.file.Path, String] =
    scala3Emitter(Module("test", Vector(CompilationUnit(namespace, decls.toVector))))

  private def emit2(namespace: String, decls: Decl*): Map[java.nio.file.Path, String] =
    scala2Emitter(Module("test", Vector(CompilationUnit(namespace, decls.toVector))))

  private def emitOne3(namespace: String, decl: Decl): String =
    emit3(namespace, decl).values.head

  private def emitOne2(namespace: String, decl: Decl): String =
    emit2(namespace, decl).values.head

  // -- Scala 3 tests -------------------------------------------------------

  test("Scala 3: Product -> class with val fields (camelCase)"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(
        Field("id", TypeExpr.LONG),
        Field("user_name", TypeExpr.STR),
      )
    ))
    assert(source.contains("package com.example"))
    assert(source.contains("class User"))
    assert(source.contains("val id: Long"))
    assert(source.contains("val userName: String"))  // snake_case -> camelCase
    assert(!source.contains("public"))
    assert(!source.contains(";"))

  test("Scala 3: Protocol -> trait with def (no fields, no get prefix)"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "UserService",
      kind = TypeKind.Protocol,
      fields = Vector(Field("id", TypeExpr.LONG)),  // should be skipped for trait
      functions = Vector(
        Func("getId", Vector.empty, TypeExpr.LONG),
        Func("findById", Vector(Param("id", TypeExpr.LONG)), TypeExpr.Optional(TypeExpr.Named("User"))),
        Func("save", Vector(Param("user", TypeExpr.Named("User"))), TypeExpr.VOID),
      )
    ))
    assert(source.contains("trait UserService"))
    assert(!source.contains("val id"))  // no fields in trait
    assert(source.contains("def id: Long"))  // getId -> id (stripped "get")
    assert(source.contains("def findById(id: Long): Option[User]"))
    assert(source.contains("def save(user: User): Unit"))

  test("Scala 3: Abstract class with equals-style body"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "AbstractEntity",
      kind = TypeKind.Abstract,
      functions = Vector(
        Func("getId", returnType = TypeExpr.LONG,
          body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, "id")))))),
        Func("validate", returnType = TypeExpr.VOID),
      )
    ))
    assert(source.contains("abstract class AbstractEntity"))
    assert(source.contains("def id: Long = this.id"))  // equals style, "get" stripped
    assert(source.contains("def validate: Unit"))  // abstract, no body

  test("Scala 3: Enum with Scala3Enum style (no values)"):
    val source = emitOne3("com.example", Decl.EnumDecl(
      name = "Color",
      variants = Vector(
        EnumVariant("RED"),
        EnumVariant("GREEN"),
        EnumVariant("BLUE"),
      )
    ))
    assert(source.contains("enum Color"))
    assert(source.contains("case Red"))
    assert(source.contains("case Green"))
    assert(source.contains("case Blue"))
    assert(!source.contains(";"))
    assert(!source.contains("(val value"))  // no constructor param for valueless enum

  test("Scala 3: Enum with values and prefix stripping"):
    val source = emitOne3("com.example", Decl.EnumDecl(
      name = "TestEnum",
      variants = Vector(
        EnumVariant("TEST_ENUM_UNKNOWN", args = Vector(Expr.Lit("0", TypeExpr.INT))),
        EnumVariant("TEST_ENUM_VALUE_ONE", args = Vector(Expr.Lit("1", TypeExpr.INT))),
        EnumVariant("TEST_ENUM_VALUE_TWO", args = Vector(Expr.Lit("2", TypeExpr.INT))),
      )
    ))
    assert(source.contains("enum TestEnum(val value: Int)"))
    assert(source.contains("case Unknown extends TestEnum(0)"))
    assert(source.contains("case ValueOne extends TestEnum(1)"))
    assert(source.contains("case ValueTwo extends TestEnum(2)"))
    assert(!source.contains("TEST_ENUM"))  // prefix stripped

  test("Scala 3: extends with"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Admin",
      kind = TypeKind.Product,
      supertypes = Vector(TypeExpr.Named("User"), TypeExpr.Named("Serializable")),
    ))
    assert(source.contains("class Admin extends User with Serializable"))
    assert(!source.contains("implements"))

  test("Scala 3: type params with upper bound"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Repository",
      kind = TypeKind.Protocol,
      typeParams = Vector(TypeParam("T"), TypeParam("ID", Vector(TypeExpr.Named("Serializable")))),
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.Named("ID"))), TypeExpr.Optional(TypeExpr.Named("T"))),
      )
    ))
    assert(source.contains("trait Repository[T, ID <: Serializable]"))
    assert(!source.contains("<T"))  // no Java-style angle brackets
    assert(!source.contains("extends Serializable"))  // uses <: not extends

  test("Scala 3: new without keyword (case class apply)"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Factory",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "create",
        returnType = TypeExpr.Named("User"),
        body = Some(Body.of(Stmt.Return(Some(
          Expr.New(TypeExpr.Named("User"), Vector(Expr.Lit("42", TypeExpr.LONG)))
        ))))
      ))
    ))
    assert(source.contains("User(42)"))
    assert(!source.contains("new User"))

  test("Scala 3: cast uses asInstanceOf"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Converter",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "convert",
        params = Vector(Param("obj", TypeExpr.Primitive.Any)),
        returnType = TypeExpr.STR,
        body = Some(Body.of(Stmt.Return(Some(
          Expr.Cast(Expr.Ref("obj"), TypeExpr.STR)
        ))))
      ))
    ))
    assert(source.contains("obj.asInstanceOf[String]"))
    assert(!source.contains("(("))

  test("Scala 3: ternary uses if-then-else"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Guard",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "check",
        params = Vector(Param("x", TypeExpr.INT)),
        returnType = TypeExpr.STR,
        body = Some(Body.of(Stmt.Return(Some(
          Expr.Ternary(
            Expr.BinOp(Expr.Ref("x"), BinaryOp.Gt, Expr.Lit("0", TypeExpr.INT)),
            Expr.Lit("\"positive\"", TypeExpr.STR),
            Expr.Lit("\"negative\"", TypeExpr.STR)
          )
        ))))
      ))
    ))
    assert(source.contains("if (x > 0) then"))
    assert(source.contains("else"))
    assert(!source.contains("?"))

  test("Scala 3: lambda uses =>"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Mapper",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "transform",
        returnType = TypeExpr.Named("List"),
        body = Some(Body.of(Stmt.Return(Some(
          Expr.Call(Some(Expr.Ref("items")), "map", Vector(
            Expr.Lambda(Vector(Param("x", TypeExpr.INT)), Body.of(
              Stmt.Return(Some(Expr.BinOp(Expr.Ref("x"), BinaryOp.Mul, Expr.Lit("2", TypeExpr.INT))))
            ))
          ))
        ))))
      ))
    ))
    assert(source.contains("=>"))
    assert(!source.contains("->"))

  test("Scala 3: Optional becomes Option[T]"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Container",
      kind = TypeKind.Product,
      fields = Vector(
        Field("value", TypeExpr.Optional(TypeExpr.STR)),
        Field("items", TypeExpr.ListOf(TypeExpr.INT)),
      )
    ))
    assert(source.contains("Option[String]"))
    assert(source.contains("List[Int]"))

  test("Scala 3: no imports for stdlib collections"):
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Data",
      kind = TypeKind.Product,
      fields = Vector(
        Field("items", TypeExpr.ListOf(TypeExpr.STR)),
        Field("mapping", TypeExpr.MapOf(TypeExpr.STR, TypeExpr.INT)),
      )
    ))
    assert(!source.contains("import"))

  test("Scala 3: file extension is .scala"):
    val files = emit3("com.example",
      Decl.TypeDecl("User", TypeKind.Product))
    val paths = files.keys.map(_.toString).toSet
    assert(paths.exists(_.endsWith(".scala")))

  // -- Scala 2 tests -------------------------------------------------------

  test("Scala 2: simple enum as sealed trait"):
    val source = emitOne2("com.example", Decl.EnumDecl(
      name = "Color",
      variants = Vector(
        EnumVariant("RED"),
        EnumVariant("GREEN"),
      )
    ))
    assert(source.contains("sealed trait Color"))
    assert(source.contains("case object Red extends Color"))
    assert(source.contains("case object Green extends Color"))
    assert(!source.contains("enum "))

  test("Scala 2: valued enum as sealed abstract class"):
    val source = emitOne2("com.example", Decl.EnumDecl(
      name = "Status",
      variants = Vector(
        EnumVariant("UNKNOWN", args = Vector(Expr.Lit("0", TypeExpr.INT))),
        EnumVariant("ACTIVE", args = Vector(Expr.Lit("1", TypeExpr.INT))),
      )
    ))
    assert(source.contains("sealed abstract class Status(val value: Int)"))
    assert(source.contains("case object Unknown extends Status(0)"))
    assert(source.contains("case object Active extends Status(1)"))

  test("Scala 2: new keyword used"):
    val source = emitOne2("com.example", Decl.TypeDecl(
      name = "Factory",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "create",
        returnType = TypeExpr.Named("User"),
        body = Some(Body.of(Stmt.Return(Some(
          Expr.New(TypeExpr.Named("User"), Vector(Expr.Lit("42", TypeExpr.LONG)))
        ))))
      ))
    ))
    assert(source.contains("new User(42)"))

  test("Scala 2: ternary uses if (cond) syntax"):
    val source = emitOne2("com.example", Decl.TypeDecl(
      name = "Guard",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "check",
        params = Vector(Param("x", TypeExpr.INT)),
        returnType = TypeExpr.BOOL,
        body = Some(Body.of(Stmt.Return(Some(
          Expr.Ternary(
            Expr.BinOp(Expr.Ref("x"), BinaryOp.Gt, Expr.Lit("0", TypeExpr.INT)),
            Expr.Lit("true", TypeExpr.BOOL),
            Expr.Lit("false", TypeExpr.BOOL)
          )
        ))))
      ))
    ))
    assert(source.contains("if ((x > 0))"))
    assert(!source.contains("then"))

  // -- Doc tests -----------------------------------------------------------

  test("Scala 3: Doc rendered as Scaladoc on type"):
    val docMeta = Meta.empty.set(Doc.key, Doc(
      summary = "Represents a user in the system.",
      params = Vector("id" -> "unique identifier"),
    ))
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(Field("id", TypeExpr.LONG)),
      meta = docMeta
    ))
    assert(source.contains("/**"))
    assert(source.contains("Represents a user in the system."))
    assert(source.contains("@param id unique identifier"))
    assert(source.contains("*/"))

  test("Scala 3: Doc on function"):
    val funcDoc = Meta.empty.set(Doc.key, Doc(
      summary = "Find user by ID.",
      returns = Some("the user if found"),
    ))
    val source = emitOne3("com.example", Decl.TypeDecl(
      name = "Service",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.LONG)),
          TypeExpr.Optional(TypeExpr.Named("User")), meta = funcDoc),
      )
    ))
    assert(source.contains("Find user by ID."))
    assert(source.contains("@return the user if found"))

  test("Scala 3: single-line doc"):
    val docMeta = Meta.empty.set(Doc.key, Doc(summary = "Simple enum."))
    val source = emitOne3("com.example", Decl.EnumDecl(
      name = "Status",
      variants = Vector(EnumVariant("ACTIVE"), EnumVariant("INACTIVE")),
      meta = docMeta
    ))
    assert(source.contains("/** Simple enum. */"))
