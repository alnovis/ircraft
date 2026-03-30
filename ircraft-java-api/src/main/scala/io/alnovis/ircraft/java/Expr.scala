package io.alnovis.ircraft.java

import scala.jdk.CollectionConverters.*

import io.alnovis.ircraft.core.TypeRef
import io.alnovis.ircraft.core.semantic.expr.*

/** Expression, Statement, and Block factory for Java consumers. */
object Expr:

  // -- Literals & References ------------------------------------------------

  def literal(value: String, typeRef: TypeRef): Expression = Expression.Literal(value, typeRef)
  def identifier(name: String): Expression                 = Expression.Identifier(name)
  def thisRef: Expression                                  = Expression.ThisRef
  def superRef: Expression                                 = Expression.SuperRef
  def nullLiteral: Expression                              = Expression.NullLiteral

  // -- Method Calls ---------------------------------------------------------

  def call(name: String): Expression.MethodCall =
    Expression.MethodCall(None, name)

  def call(receiver: Expression, name: String): Expression.MethodCall =
    Expression.MethodCall(Some(receiver), name)

  def call(receiver: Expression, name: String, args: java.util.List[Expression]): Expression.MethodCall =
    Expression.MethodCall(Some(receiver), name, args.asScala.toList)

  def call(name: String, args: java.util.List[Expression]): Expression.MethodCall =
    Expression.MethodCall(None, name, args.asScala.toList)

  // -- Operators ------------------------------------------------------------

  def binOp(left: Expression, op: BinOperator, right: Expression): Expression.BinaryOp =
    Expression.BinaryOp(left, op, right)

  def unaryOp(op: UnOperator, expr: Expression): Expression.UnaryOp =
    Expression.UnaryOp(op, expr)

  // -- BinOperator constants ------------------------------------------------

  val ADD: BinOperator = BinOperator.Add
  val SUB: BinOperator = BinOperator.Sub
  val MUL: BinOperator = BinOperator.Mul
  val DIV: BinOperator = BinOperator.Div
  val MOD: BinOperator = BinOperator.Mod
  val EQ: BinOperator  = BinOperator.Eq
  val NEQ: BinOperator = BinOperator.Neq
  val LT: BinOperator  = BinOperator.Lt
  val GT: BinOperator  = BinOperator.Gt
  val LTE: BinOperator = BinOperator.Lte
  val GTE: BinOperator = BinOperator.Gte
  val AND: BinOperator = BinOperator.And
  val OR: BinOperator  = BinOperator.Or

  // -- UnOperator constants -------------------------------------------------

  val NOT: UnOperator         = UnOperator.Not
  val NEGATE: UnOperator      = UnOperator.Negate
  val BITWISE_NOT: UnOperator = UnOperator.BitwiseNot

  // -- Other Expressions ----------------------------------------------------

  def fieldAccess(receiver: Expression, name: String): Expression.FieldAccess =
    Expression.FieldAccess(receiver, name)

  def cast(expr: Expression, target: TypeRef): Expression.Cast =
    Expression.Cast(expr, target)

  def newInstance(typeRef: TypeRef, args: java.util.List[Expression]): Expression.NewInstance =
    Expression.NewInstance(typeRef, args.asScala.toList)

  def newInstance(typeRef: TypeRef): Expression.NewInstance =
    Expression.NewInstance(typeRef)

  def conditional(cond: Expression, thenExpr: Expression, elseExpr: Expression): Expression.Conditional =
    Expression.Conditional(cond, thenExpr, elseExpr)

  def lambda(params: java.util.List[String], body: Expression): Expression.Lambda =
    Expression.Lambda(params.asScala.toList, body)

  // -- Statements -----------------------------------------------------------

  def returnStmt(expr: Expression): Statement.ReturnStmt =
    Statement.ReturnStmt(Some(expr))

  def returnVoid: Statement.ReturnStmt =
    Statement.ReturnStmt(None)

  def ifStmt(cond: Expression, thenBlock: Block): Statement.IfStmt =
    Statement.IfStmt(cond, thenBlock)

  def ifElse(cond: Expression, thenBlock: Block, elseBlock: Block): Statement.IfStmt =
    Statement.IfStmt(cond, thenBlock, Some(elseBlock))

  def exprStmt(expr: Expression): Statement.ExpressionStmt =
    Statement.ExpressionStmt(expr)

  def varDecl(name: String, typeRef: TypeRef, init: Expression): Statement.VarDecl =
    Statement.VarDecl(name, typeRef, Some(init))

  def assign(target: Expression, value: Expression): Statement.Assignment =
    Statement.Assignment(target, value)

  def throwStmt(expr: Expression): Statement.ThrowStmt =
    Statement.ThrowStmt(expr)

  def forEach(variable: String, varType: TypeRef, iterable: Expression, body: Block): Statement.ForEachStmt =
    Statement.ForEachStmt(variable, varType, iterable, body)

  // -- Block ----------------------------------------------------------------

  def block(stmts: java.util.List[Statement]): Block =
    Block(stmts.asScala.toList)

  @scala.annotation.varargs
  def block(stmts: Statement*): Block = Block(stmts.toList)

  val emptyBlock: Block = Block.empty
