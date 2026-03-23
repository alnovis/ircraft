package io.alnovis.ircraft.dialect.semantic.expr

import io.alnovis.ircraft.core.{ContentHash, ContentHashable, TypeRef}

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

  given ContentHashable[Expression] with

    def contentHash(a: Expression): Int =
      val typeRefHash = summon[ContentHashable[TypeRef]]
      a match
        case Literal(v, t)              => ContentHash.combine(1, ContentHash.ofString(v), typeRefHash.contentHash(t))
        case Identifier(n)              => ContentHash.combine(2, ContentHash.ofString(n))
        case MethodCall(recv, n, args, typeArgs) =>
          ContentHash.combine(
            3,
            recv.map(contentHash).getOrElse(0),
            ContentHash.ofString(n),
            ContentHash.ofList(args)(using this),
            ContentHash.ofList(typeArgs)(using typeRefHash)
          )
        case FieldAccess(recv, n)       => ContentHash.combine(4, contentHash(recv), ContentHash.ofString(n))
        case NewInstance(t, args)        => ContentHash.combine(5, typeRefHash.contentHash(t), ContentHash.ofList(args)(using this))
        case Cast(expr, t)              => ContentHash.combine(6, contentHash(expr), typeRefHash.contentHash(t))
        case Conditional(c, t, f)       => ContentHash.combine(7, contentHash(c), contentHash(t), contentHash(f))
        case BinaryOp(l, op, r)         => ContentHash.combine(8, contentHash(l), op.ordinal, contentHash(r))
        case UnaryOp(op, expr)          => ContentHash.combine(9, op.ordinal, contentHash(expr))
        case Lambda(params, body)       => ContentHash.combine(10, ContentHash.ofList(params), contentHash(body))
        case NullLiteral                => 11
        case ThisRef                    => 12
        case SuperRef                   => 13

/** Binary operators. */
enum BinOperator:
  case Eq, Neq, Lt, Gt, Lte, Gte
  case Add, Sub, Mul, Div, Mod
  case And, Or
  case BitAnd, BitOr, BitXor

/** Unary operators. */
enum UnOperator:
  case Not, Negate, BitwiseNot
