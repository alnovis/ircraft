package io.alnovis.ircraft.core.ir

sealed trait Expr

object Expr {
  case class Lit(value: String, litType: TypeExpr) extends Expr
  case class Ref(name: String)                     extends Expr
  case object Null                                 extends Expr
  case object This                                 extends Expr
  case object Super                                extends Expr

  case class Access(expr: Expr, field: String) extends Expr
  case class Index(expr: Expr, index: Expr)    extends Expr

  case class Call(
    receiver: Option[Expr],
    name: String,
    args: Vector[Expr] = Vector.empty,
    typeArgs: Vector[TypeExpr] = Vector.empty
  ) extends Expr

  case class New(typeExpr: TypeExpr, args: Vector[Expr] = Vector.empty) extends Expr

  case class BinOp(left: Expr, op: BinaryOp, right: Expr)     extends Expr
  case class UnOp(op: UnaryOp, expr: Expr)                    extends Expr
  case class Ternary(cond: Expr, ifTrue: Expr, ifFalse: Expr) extends Expr

  case class Cast(expr: Expr, target: TypeExpr)        extends Expr
  case class Lambda(params: Vector[Param], body: Body) extends Expr
  case class TypeRef(typeExpr: TypeExpr)               extends Expr
}

sealed abstract class BinaryOp

object BinaryOp {
  case object Eq     extends BinaryOp
  case object Neq    extends BinaryOp
  case object Lt     extends BinaryOp
  case object Gt     extends BinaryOp
  case object Lte    extends BinaryOp
  case object Gte    extends BinaryOp
  case object Add    extends BinaryOp
  case object Sub    extends BinaryOp
  case object Mul    extends BinaryOp
  case object Div    extends BinaryOp
  case object Mod    extends BinaryOp
  case object And    extends BinaryOp
  case object Or     extends BinaryOp
  case object BitAnd extends BinaryOp
  case object BitOr  extends BinaryOp
  case object BitXor extends BinaryOp
}

sealed abstract class UnaryOp

object UnaryOp {
  case object Not    extends UnaryOp
  case object Negate extends UnaryOp
  case object BitNot extends UnaryOp
}
