package io.alnovis.ircraft.dialect.semantic

import io.alnovis.ircraft.core.TypeRef
import io.alnovis.ircraft.dialect.semantic.expr.*
import io.alnovis.ircraft.dialect.semantic.expr.ExprTraversal.*

class ExprTraversalSuite extends munit.FunSuite:

  // ── Expression.walk ────────────────────────────────────────────────────

  test("walk visits all sub-expressions"):
    val expr = Expression.MethodCall(
      Some(Expression.FieldAccess(Expression.ThisRef, "proto")),
      "getAmount",
      List(Expression.Literal("1", TypeRef.INT))
    )
    var visited = List.empty[String]
    expr.walk:
      case Expression.MethodCall(_, name, _, _) => visited = visited :+ s"call:$name"
      case Expression.FieldAccess(_, name)      => visited = visited :+ s"field:$name"
      case Expression.ThisRef                   => visited = visited :+ "this"
      case Expression.Literal(v, _)             => visited = visited :+ s"lit:$v"
      case _                                    => ()

    assertEquals(visited, List("call:getAmount", "field:proto", "this", "lit:1"))

  // ── Expression.collectAll ──────────────────────────────────────────────

  test("collectAll finds nested MethodCalls"):
    val expr = Expression.MethodCall(
      Some(Expression.MethodCall(None, "inner")),
      "outer",
      List(Expression.MethodCall(None, "arg"))
    )
    val calls = expr.collectAll { case Expression.MethodCall(_, name, _, _) => name }
    assertEquals(calls, List("outer", "inner", "arg"))

  test("collectAll finds Identifiers in BinaryOp"):
    val expr = Expression.BinaryOp(
      Expression.Identifier("a"),
      BinOperator.Add,
      Expression.Identifier("b")
    )
    val ids = expr.collectAll { case Expression.Identifier(n) => n }
    assertEquals(ids, List("a", "b"))

  // ── Expression.transform ───────────────────────────────────────────────

  test("transform replaces Identifiers"):
    val expr = Expression.MethodCall(
      Some(Expression.Identifier("proto")),
      "get",
      List(Expression.Identifier("proto"))
    )
    val transformed = expr.transform {
      case Expression.Identifier("proto") =>
        Expression.Identifier("delegate")
    }
    val ids = transformed.collectAll { case Expression.Identifier(n) => n }
    assertEquals(ids, List("delegate", "delegate"))

  test("transform is bottom-up"):
    val expr = Expression.FieldAccess(Expression.Identifier("x"), "y")
    val transformed = expr.transform:
      case Expression.Identifier("x") => Expression.Identifier("replaced")
      case Expression.FieldAccess(Expression.Identifier("replaced"), n) =>
        Expression.Identifier(s"found_$n")
    // Bottom-up: first x->replaced, then FieldAccess(replaced, y) -> found_y
    assertEquals(transformed, Expression.Identifier("found_y"))

  // ── Statement.walkExprs ────────────────────────────────────────────────

  test("walkExprs finds expressions in ReturnStmt"):
    val stmt  = Statement.ReturnStmt(Some(Expression.Identifier("result")))
    var found = List.empty[String]
    stmt.walkExprs {
      case Expression.Identifier(n) => found = found :+ n
      case _                        => ()
    }
    assertEquals(found, List("result"))

  test("walkExprs recurses into IfStmt blocks"):
    val stmt = Statement.IfStmt(
      Expression.Identifier("cond"),
      Block.of(Statement.ReturnStmt(Some(Expression.Identifier("then")))),
      Some(Block.of(Statement.ReturnStmt(Some(Expression.Identifier("else")))))
    )
    var found = List.empty[String]
    stmt.walkExprs {
      case Expression.Identifier(n) => found = found :+ n
      case _                        => ()
    }
    assertEquals(found, List("cond", "then", "else"))

  // ── Statement.transformExprs ───────────────────────────────────────────

  test("transformExprs replaces in nested blocks"):
    val stmt = Statement.IfStmt(
      Expression.Identifier("x"),
      Block.of(Statement.ReturnStmt(Some(Expression.Identifier("x")))),
      None
    )
    val transformed = stmt.transformExprs {
      case Expression.Identifier("x") =>
        Expression.Identifier("y")
    }
    val ids = transformed.collectExprs { case Expression.Identifier(n) => n }
    assertEquals(ids, List("y", "y"))

  // ── Block.walkExprs ────────────────────────────────────────────────────

  test("Block.walkExprs traverses all statements"):
    val block = Block.of(
      Statement.VarDecl("a", TypeRef.INT, Some(Expression.Literal("1", TypeRef.INT))),
      Statement.ReturnStmt(Some(Expression.Identifier("a")))
    )
    var found = List.empty[String]
    block.walkExprs:
      case Expression.Literal(v, _) => found = found :+ s"lit:$v"
      case Expression.Identifier(n) => found = found :+ s"id:$n"
      case _                        => ()
    assertEquals(found, List("lit:1", "id:a"))

  // ── Block.collectStmts ─────────────────────────────────────────────────

  test("collectStmts finds ReturnStmt in nested IfStmt"):
    val block = Block.of(
      Statement.IfStmt(
        Expression.Identifier("cond"),
        Block.of(Statement.ReturnStmt(Some(Expression.Literal("1", TypeRef.INT)))),
        Some(Block.of(Statement.ReturnStmt(Some(Expression.Literal("2", TypeRef.INT)))))
      )
    )
    val returns = block.collectStmts { case r: Statement.ReturnStmt => r }
    assertEquals(returns.size, 2)

  test("collectStmts finds VarDecl at top level"):
    val block = Block.of(
      Statement.VarDecl("x", TypeRef.INT),
      Statement.VarDecl("y", TypeRef.STRING)
    )
    val decls = block.collectStmts { case Statement.VarDecl(n, _, _, _) => n }
    assertEquals(decls, List("x", "y"))

  // ── Block.walkStmts ────────────────────────────────────────────────────

  test("walkStmts visits all statements including nested"):
    val block = Block.of(
      Statement.ExpressionStmt(Expression.Identifier("a")),
      Statement.IfStmt(
        Expression.Identifier("cond"),
        Block.of(Statement.ExpressionStmt(Expression.Identifier("b"))),
        None
      )
    )
    var count = 0
    block.walkStmts(_ => count += 1)
    assertEquals(count, 3) // ExprStmt + IfStmt + nested ExprStmt

  // ── Block.transformExprs ───────────────────────────────────────────────

  test("Block.transformExprs replaces across all statements"):
    val block = Block.of(
      Statement.ExpressionStmt(Expression.Identifier("old")),
      Statement.ReturnStmt(Some(Expression.Identifier("old")))
    )
    val transformed = block.transformExprs {
      case Expression.Identifier("old") =>
        Expression.Identifier("new")
    }
    val ids = transformed.collectExprs { case Expression.Identifier(n) => n }
    assertEquals(ids, List("new", "new"))
