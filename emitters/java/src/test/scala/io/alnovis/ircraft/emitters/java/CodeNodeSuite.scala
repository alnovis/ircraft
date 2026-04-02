package io.alnovis.ircraft.emitters.java

import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emit.{CodeNode, Renderer}

/** Tests at the CodeNode (tree) level -- structural, not string-based. */
class CodeNodeSuite extends munit.FunSuite:

  type F[A] = Id[A]
  private val emitter = JavaEmitter[F]

  private def toTree(namespace: String, decl: Decl): CodeNode =
    val module = Module("test", Vector(CompilationUnit(namespace, Vector(decl))))
    val (_, files) = Pipe.run(emitter(module))
    // We need the CodeNode, not the rendered string.
    // Use the emitter's internal method via a helper.
    val (_, tree) = Pipe.run(emitter.toFileTree(namespace, decl))
    tree

  test("TypeDecl produces TypeBlock with correct sections"):
    val tree = toTree("com.example", Decl.TypeDecl(
      name = "User",
      kind = TypeKind.Product,
      fields = Vector(
        Field("id", TypeExpr.LONG),
        Field("name", TypeExpr.STR),
      ),
      functions = Vector(
        Func("getId", returnType = TypeExpr.LONG,
          body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, "id")))))),
      )
    ))

    tree match
      case CodeNode.File(header, _, Vector(CodeNode.TypeBlock(sig, sections))) =>
        assertEquals(header, "package com.example")
        assert(sig.contains("public class User"))
        assertEquals(sections.size, 2) // fields section + methods section
        // fields section
        assertEquals(sections(0).size, 2) // id, name
        // methods section
        assertEquals(sections(1).size, 1) // getId
        sections(1).head match
          case CodeNode.Func(funcSig, Some(body)) =>
            assert(funcSig.contains("getId"))
            assertEquals(body.size, 1) // return statement
          case other => fail(s"expected Func, got $other")
      case other => fail(s"unexpected tree structure: $other")

  test("Protocol produces TypeBlock with abstract Func"):
    val tree = toTree("com.example", Decl.TypeDecl(
      name = "Service",
      kind = TypeKind.Protocol,
      functions = Vector(
        Func("find", Vector(Param("id", TypeExpr.LONG)), TypeExpr.Named("User")),
      )
    ))

    tree match
      case CodeNode.File(_, _, Vector(CodeNode.TypeBlock(sig, sections))) =>
        assert(sig.contains("interface Service"))
        val funcs = sections.flatten
        assertEquals(funcs.size, 1)
        funcs.head match
          case CodeNode.Func(_, None) => () // abstract -- no body
          case other => fail(s"expected abstract Func (None body), got $other")
      case other => fail(s"unexpected: $other")

  test("IfElse produces correct tree structure"):
    val tree = toTree("com.example", Decl.TypeDecl(
      name = "Guard",
      kind = TypeKind.Product,
      functions = Vector(Func(
        name = "check",
        params = Vector(Param("x", TypeExpr.INT)),
        returnType = TypeExpr.BOOL,
        body = Some(Body.of(
          Stmt.If(
            Expr.BinOp(Expr.Ref("x"), BinaryOp.Gt, Expr.Lit("0", TypeExpr.INT)),
            Body.of(Stmt.Return(Some(Expr.Lit("true", TypeExpr.BOOL)))),
            Some(Body.of(Stmt.Return(Some(Expr.Lit("false", TypeExpr.BOOL)))))
          )
        ))
      ))
    ))

    tree match
      case CodeNode.File(_, _, Vector(CodeNode.TypeBlock(_, sections))) =>
        val func = sections.flatten.head
        func match
          case CodeNode.Func(_, Some(Vector(ifElse: CodeNode.IfElse))) =>
            assert(ifElse.cond.contains("x") && ifElse.cond.contains(">") && ifElse.cond.contains("0"))
            assertEquals(ifElse.thenBody.size, 1)
            assert(ifElse.elseBody.isDefined)
            assertEquals(ifElse.elseBody.get.size, 1)
          case other => fail(s"expected Func with IfElse body, got $other")
      case other => fail(s"unexpected: $other")

  test("nested TypeBlock inside TypeBlock"):
    val tree = toTree("com.example", Decl.TypeDecl(
      name = "Outer",
      kind = TypeKind.Product,
      fields = Vector(Field("x", TypeExpr.INT)),
      nested = Vector(
        Decl.TypeDecl("Inner", TypeKind.Product, fields = Vector(Field("y", TypeExpr.STR)))
      )
    ))

    tree match
      case CodeNode.File(_, _, Vector(CodeNode.TypeBlock(sig, sections))) =>
        assert(sig.contains("Outer"))
        // should have fields + nested sections
        val nestedSection = sections.last
        nestedSection.head match
          case CodeNode.TypeBlock(innerSig, innerSections) =>
            assert(innerSig.contains("Inner"))
            assert(innerSections.nonEmpty) // fields
          case other => fail(s"expected nested TypeBlock, got $other")
      case other => fail(s"unexpected: $other")

  test("render produces valid Java from CodeNode"):
    val tree = CodeNode.File(
      "package com.example",
      Vector("java.util.List"),
      Vector(CodeNode.TypeBlock(
        "public class Demo",
        Vector(
          Vector(CodeNode.Line("private final int count;")),
          Vector(CodeNode.Func(
            "public int getCount()",
            Some(Vector(CodeNode.Line("return this.count;")))
          ))
        )
      ))
    )
    val source = Renderer.render(tree, ";")

    assert(source.contains("package com.example;"))
    assert(source.contains("import java.util.List;"))
    assert(source.contains("public class Demo {"))
    assert(source.contains("    private final int count;"))
    assert(source.contains("    public int getCount() {"))
    assert(source.contains("        return this.count;"))

  test("Renderer IfElse formats else on same line as closing brace"):
    val tree = CodeNode.IfElse(
      "x > 0",
      Vector(CodeNode.Line("return true;")),
      Some(Vector(CodeNode.Line("return false;")))
    )
    val rendered = Renderer.render(tree)
    assert(rendered.contains("} else {"))
    assert(!rendered.contains("}\n") || rendered.contains("} else {"))
