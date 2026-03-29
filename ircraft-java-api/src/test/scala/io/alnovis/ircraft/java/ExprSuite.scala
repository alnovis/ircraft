package io.alnovis.ircraft.java

import io.alnovis.ircraft.dialect.semantic.expr.*

class ExprSuite extends munit.FunSuite:

  test("literal creates Literal"):
    val e = Expr.literal("42", Types.INT)
    assert(e.isInstanceOf[Expression.Literal])
    assertEquals(e.asInstanceOf[Expression.Literal].value, "42")

  test("identifier creates Identifier"):
    assertEquals(Expr.identifier("x"), Expression.Identifier("x"))

  test("thisRef and nullLiteral"):
    assertEquals(Expr.thisRef, Expression.ThisRef)
    assertEquals(Expr.nullLiteral, Expression.NullLiteral)

  test("call without receiver"):
    val c = Expr.call("getName")
    assertEquals(c.receiver, None)
    assertEquals(c.name, "getName")
    assertEquals(c.args, Nil)

  test("call with receiver"):
    val c = Expr.call(Expr.identifier("obj"), "getName")
    assertEquals(c.receiver, Some(Expression.Identifier("obj")))

  test("call with receiver and args"):
    val c = Expr.call(Expr.identifier("obj"), "equals", java.util.List.of(Expr.identifier("other")))
    assertEquals(c.args.size, 1)

  test("call without receiver with args"):
    val c = Expr.call("compute", java.util.List.of(Expr.literal("1", Types.INT)))
    assertEquals(c.receiver, None)
    assertEquals(c.args.size, 1)

  test("binOp creates BinaryOp"):
    val e = Expr.binOp(Expr.literal("1", Types.INT), Expr.ADD, Expr.literal("2", Types.INT))
    assert(e.isInstanceOf[Expression.BinaryOp])
    assertEquals(e.op, BinOperator.Add)

  test("all binOp constants"):
    assertEquals(Expr.ADD, BinOperator.Add)
    assertEquals(Expr.SUB, BinOperator.Sub)
    assertEquals(Expr.MUL, BinOperator.Mul)
    assertEquals(Expr.DIV, BinOperator.Div)
    assertEquals(Expr.MOD, BinOperator.Mod)
    assertEquals(Expr.EQ, BinOperator.Eq)
    assertEquals(Expr.NEQ, BinOperator.Neq)
    assertEquals(Expr.LT, BinOperator.Lt)
    assertEquals(Expr.GT, BinOperator.Gt)
    assertEquals(Expr.LTE, BinOperator.Lte)
    assertEquals(Expr.GTE, BinOperator.Gte)
    assertEquals(Expr.AND, BinOperator.And)
    assertEquals(Expr.OR, BinOperator.Or)

  test("fieldAccess creates FieldAccess"):
    val e = Expr.fieldAccess(Expr.identifier("obj"), "proto")
    assertEquals(e.name, "proto")

  test("cast creates Cast"):
    val e = Expr.cast(Expr.identifier("obj"), Types.named("Money"))
    assert(e.isInstanceOf[Expression.Cast])

  test("newInstance with args"):
    val e = Expr.newInstance(Types.named("Money"), java.util.List.of(Expr.literal("1", Types.INT)))
    assertEquals(e.args.size, 1)

  test("newInstance without args"):
    val e = Expr.newInstance(Types.named("Money"))
    assertEquals(e.args, Nil)

  test("returnStmt"):
    val s = Expr.returnStmt(Expr.identifier("x"))
    assertEquals(s.value, Some(Expression.Identifier("x")))

  test("returnVoid"):
    assertEquals(Expr.returnVoid.value, None)

  test("ifStmt"):
    val s = Expr.ifStmt(Expr.identifier("cond"), Expr.block(Expr.returnVoid))
    assertEquals(s.elseBlock, None)

  test("ifElse"):
    val thenB = Expr.block(Expr.returnStmt(Expr.literal("1", Types.INT)))
    val elseB = Expr.block(Expr.returnStmt(Expr.literal("0", Types.INT)))
    val s     = Expr.ifElse(Expr.identifier("cond"), thenB, elseB)
    assert(s.elseBlock.isDefined)

  test("block from java.util.List"):
    val stmts = java.util.List.of[Statement](
      Expr.exprStmt(Expr.call("println")),
      Expr.returnVoid
    )
    val b = Expr.block(stmts)
    assertEquals(b.statements.size, 2)

  test("block from varargs"):
    val b = Expr.block(Expr.exprStmt(Expr.call("a")), Expr.returnVoid)
    assertEquals(b.statements.size, 2)

  test("emptyBlock"):
    assertEquals(Expr.emptyBlock.statements, Nil)

  test("varDecl creates VarDecl"):
    val s = Expr.varDecl("x", Types.INT, Expr.literal("0", Types.INT))
    assertEquals(s.name, "x")

  test("assign creates Assignment"):
    val s = Expr.assign(Expr.identifier("x"), Expr.literal("1", Types.INT))
    assert(s.isInstanceOf[Statement.Assignment])

  test("throwStmt creates ThrowStmt"):
    val s = Expr.throwStmt(Expr.newInstance(Types.named("RuntimeException")))
    assert(s.isInstanceOf[Statement.ThrowStmt])

  test("forEach creates ForEachStmt"):
    val s = Expr.forEach("item", Types.STRING, Expr.identifier("list"), Expr.block(Expr.exprStmt(Expr.call("process"))))
    assert(s.isInstanceOf[Statement.ForEachStmt])
