package io.alnovis.ircraft.emitters.java

import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*

class JavaEmitterSuite extends munit.FunSuite:

  type F[A] = Id[A]
  private val emitter = JavaEmitter[F]

  private def emit(namespace: String, decls: Decl*): Map[java.nio.file.Path, String] =
    val module = Module("test", Vector(CompilationUnit(namespace, decls.toVector)))
    val (_, files) = Pipe.run(emitter(module))
    files

  private def emitOne(namespace: String, decl: Decl): String =
    emit(namespace, decl).values.head

  test("emit simple Product (class) with fields"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(
        Field("id", TypeExpr.LONG),
        Field("name", TypeExpr.STR),
      )
    ))
    assert(source.contains("package com.example;"))
    assert(source.contains("public class User"))
    assert(source.contains("public final long id;"))
    assert(source.contains("public final String name;"))

  test("emit Protocol (interface) with methods"):
    val source = emitOne("com.example.api", Decl.TypeDecl(
      name = "UserService",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.LONG)), TypeExpr.Optional(TypeExpr.Named("User"))),
        Func("save", Vector(Param("user", TypeExpr.Named("User"))), TypeExpr.VOID),
      )
    ))
    assert(source.contains("public interface UserService"))
    assert(source.contains("User findById(long id);"))
    assert(source.contains("void save(User user);"))

  test("emit Abstract class"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "AbstractEntity",
      kind = TypeKind.Abstract,
      fields = Vector(Field("id", TypeExpr.LONG)),
      functions = Vector(
        Func("getId", returnType = TypeExpr.LONG,
          body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, "id")))))),
        Func("validate", returnType = TypeExpr.VOID),
      )
    ))
    assert(source.contains("public abstract class AbstractEntity"))
    assert(source.contains("public long getId()"))
    assert(source.contains("return this.id;"))
    assert(source.contains("abstract void validate();"))

  test("emit EnumDecl"):
    val source = emitOne("com.example", Decl.EnumDecl(
      name = "Color",
      variants = Vector(
        EnumVariant("RED"),
        EnumVariant("GREEN"),
        EnumVariant("BLUE"),
      )
    ))
    assert(source.contains("public enum Color"))
    assert(source.contains("RED,"))
    assert(source.contains("GREEN,"))
    assert(source.contains("BLUE;"))

  test("emit valued enum with constructor"):
    val source = emitOne("com.example", Decl.EnumDecl(
      name = "HttpStatus",
      variants = Vector(
        EnumVariant("OK", args = Vector(Expr.Lit("200", TypeExpr.INT))),
        EnumVariant("NOT_FOUND", args = Vector(Expr.Lit("404", TypeExpr.INT))),
      ),
      functions = Vector(
        Func("getCode", returnType = TypeExpr.INT,
          body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, "code"))))))
      )
    ))
    assert(source.contains("OK(200),"))
    assert(source.contains("NOT_FOUND(404);"))
    assert(source.contains("public int getCode()"))

  test("emit interface with default method"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "Printable",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("print", returnType = TypeExpr.VOID),
        Func("printLn", returnType = TypeExpr.VOID,
          modifiers = Set(FuncModifier.Default),
          body = Some(Body.of(
            Stmt.Eval(Expr.Call(Some(Expr.This), "print", Vector.empty)),
            Stmt.Eval(Expr.Call(None, "println", Vector(Expr.Lit("\"\"", TypeExpr.STR))))
          )))
      )
    ))
    assert(source.contains("void print();"))
    assert(source.contains("default void printLn()"))

  test("emit class extending superclass and implementing interface"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "Admin",
      kind = TypeKind.Product,
      supertypes = Vector(TypeExpr.Named("User"), TypeExpr.Named("Serializable")),
      fields = Vector(Field("role", TypeExpr.STR))
    ))
    assert(source.contains("public class Admin extends User implements Serializable"))

  test("emit interface extending interfaces"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "ReadWriteStore",
      kind = TypeKind.Protocol,
      supertypes = Vector(TypeExpr.Named("ReadStore"), TypeExpr.Named("WriteStore")),
    ))
    assert(source.contains("public interface ReadWriteStore extends ReadStore, WriteStore"))

  test("emit class with generic type params"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "Repository",
      kind = TypeKind.Protocol,
      typeParams = Vector(TypeParam("T"), TypeParam("ID", Vector(TypeExpr.Named("Serializable")))),
      functions = Vector(
        Func("findById", Vector(Param("id", TypeExpr.Named("ID"))), TypeExpr.Optional(TypeExpr.Named("T"))),
      )
    ))
    assert(source.contains("public interface Repository<T, ID extends Serializable>"))

  test("imports are collected for List/Map types"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "Container",
      kind = TypeKind.Product,
      fields = Vector(
        Field("items", TypeExpr.ListOf(TypeExpr.STR)),
        Field("mapping", TypeExpr.MapOf(TypeExpr.STR, TypeExpr.INT)),
      )
    ))
    assert(source.contains("import java.util.List;"))
    assert(source.contains("import java.util.Map;"))
    assert(source.contains("List<String> items"))
    assert(source.contains("Map<String, Integer> mapping"))

  test("emit method with body: if/return"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "Guard",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "check",
        params = Vector(Param("value", TypeExpr.INT)),
        returnType = TypeExpr.BOOL,
        body = Some(Body.of(
          Stmt.If(
            Expr.BinOp(Expr.Ref("value"), BinaryOp.Gt, Expr.Lit("0", TypeExpr.INT)),
            Body.of(Stmt.Return(Some(Expr.Lit("true", TypeExpr.BOOL)))),
            Some(Body.of(Stmt.Return(Some(Expr.Lit("false", TypeExpr.BOOL)))))
          )
        ))
      ))
    ))
    assert(source.contains("if ((value > 0))"))
    assert(source.contains("return true;"))
    assert(source.contains("else {"))
    assert(source.contains("return false;"))

  test("emit multiple files from one module"):
    val files = emit("com.example",
      Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG))),
      Decl.EnumDecl("Role", variants = Vector(EnumVariant("ADMIN"), EnumVariant("USER"))),
    )
    assertEquals(files.size, 2)
    val paths = files.keys.map(_.toString).toSet
    assert(paths.contains("com/example/User.java"))
    assert(paths.contains("com/example/Role.java"))

  test("emit nested types"):
    val inner = Decl.TypeDecl("Address", TypeKind.Product, fields = Vector(Field("city", TypeExpr.STR)))
    val outer = Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(Field("name", TypeExpr.STR)),
      nested = Vector(inner)
    )
    val source = emitOne("com.example", outer)
    assert(source.contains("public class User"))
    assert(source.contains("public class Address"))
    assert(source.contains("public final String city;"))

  test("emit annotations"):
    val source = emitOne("com.example", Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      annotations = Vector(Annotation("Entity")),
      fields = Vector(
        Field("id", TypeExpr.LONG, annotations = Vector(Annotation("Id"), Annotation("GeneratedValue"))),
        Field("name", TypeExpr.STR, annotations = Vector(Annotation("NotNull"))),
      )
    ))
    assert(source.contains("@Entity"))
    assert(source.contains("@Id"))
    assert(source.contains("@GeneratedValue"))
    assert(source.contains("@NotNull"))
