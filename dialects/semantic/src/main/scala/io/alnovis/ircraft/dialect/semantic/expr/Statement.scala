package io.alnovis.ircraft.dialect.semantic.expr

import io.alnovis.ircraft.core.TypeRef

/** Language-agnostic code statement. */
sealed trait Statement

object Statement:
  case class ExpressionStmt(expr: Expression)             extends Statement
  case class ReturnStmt(value: Option[Expression] = None) extends Statement

  case class VarDecl(name: String, varType: TypeRef, initializer: Option[Expression] = None, isFinal: Boolean = false)
      extends Statement
  case class Assignment(target: Expression, value: Expression)                                  extends Statement
  case class IfStmt(cond: Expression, thenBlock: Block, elseBlock: Option[Block] = None)        extends Statement
  case class ForEachStmt(variable: String, varType: TypeRef, iterable: Expression, body: Block) extends Statement
  case class ThrowStmt(expr: Expression)                                                        extends Statement

  case class TryCatchStmt(tryBlock: Block, catches: List[CatchClause], finallyBlock: Option[Block] = None)
      extends Statement

/** A sequence of statements. */
case class Block(statements: List[Statement])

object Block:
  val empty: Block = Block(Nil)

  def of(stmts: Statement*): Block = Block(stmts.toList)

/** Catch clause for try-catch statements. */
case class CatchClause(
  exceptionType: TypeRef,
  variableName: String,
  body: Block
)
