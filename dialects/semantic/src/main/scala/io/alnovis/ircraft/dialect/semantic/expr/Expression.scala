package io.alnovis.ircraft.dialect.semantic.expr

import io.alnovis.ircraft.core.TypeRef

/**
  * Language-agnostic code expression. Used in method bodies, field initializers, and constructor bodies.
  *
  * This is intentionally minimal — it covers the most common patterns needed for code generation. Language-specific
  * expressions belong in code dialect operations.
  */
sealed trait Expression

object Expression:
  case class Literal(value: String, litType: TypeRef) extends Expression
  case class Identifier(name: String)                 extends Expression

  case class MethodCall(
    receiver: Option[Expression],
    name: String,
    args: List[Expression] = Nil,
    typeArgs: List[TypeRef] = Nil
  ) extends Expression
  case class FieldAccess(receiver: Expression, name: String)                           extends Expression
  case class NewInstance(classType: TypeRef, args: List[Expression] = Nil)             extends Expression
  case class Cast(expr: Expression, targetType: TypeRef)                               extends Expression
  case class Conditional(cond: Expression, thenExpr: Expression, elseExpr: Expression) extends Expression
  case class BinaryOp(left: Expression, op: BinOperator, right: Expression)            extends Expression
  case class UnaryOp(op: UnOperator, expr: Expression)                                 extends Expression
  case class Lambda(params: List[String], body: Expression)                            extends Expression
  case object NullLiteral                                                              extends Expression
  case object ThisRef                                                                  extends Expression
  case object SuperRef                                                                 extends Expression

/** Binary operators. */
enum BinOperator:
  case Eq, Neq, Lt, Gt, Lte, Gte
  case Add, Sub, Mul, Div, Mod
  case And, Or
  case BitAnd, BitOr, BitXor

/** Unary operators. */
enum UnOperator:
  case Not, Negate, BitwiseNot
