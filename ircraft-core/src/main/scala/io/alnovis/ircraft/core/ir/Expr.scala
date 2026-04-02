package io.alnovis.ircraft.core.ir

sealed trait Expr

object Expr:
  // -- Atoms --
  case class Lit(value: String, litType: TypeExpr) extends Expr
  case class Ref(name: String) extends Expr
  case object Null extends Expr
  case object This extends Expr
  case object Super extends Expr

  // -- Access --
  case class Access(expr: Expr, field: String) extends Expr
  case class Index(expr: Expr, index: Expr) extends Expr

  // -- Calls --
  case class Call(
    receiver: Option[Expr],
    name: String,
    args: Vector[Expr] = Vector.empty,
    typeArgs: Vector[TypeExpr] = Vector.empty
  ) extends Expr

  case class New(typeExpr: TypeExpr, args: Vector[Expr] = Vector.empty) extends Expr

  // -- Operators --
  case class BinOp(left: Expr, op: BinaryOp, right: Expr) extends Expr
  case class UnOp(op: UnaryOp, expr: Expr) extends Expr
  case class Ternary(cond: Expr, ifTrue: Expr, ifFalse: Expr) extends Expr

  // -- Cast --
  case class Cast(expr: Expr, target: TypeExpr) extends Expr

  // -- Lambda --
  case class Lambda(params: Vector[Param], body: Body) extends Expr

  // -- Type as expression (e.g., ClassName::method) --
  case class TypeRef(typeExpr: TypeExpr) extends Expr

enum BinaryOp:
  case Eq, Neq, Lt, Gt, Lte, Gte
  case Add, Sub, Mul, Div, Mod
  case And, Or
  case BitAnd, BitOr, BitXor

enum UnaryOp:
  case Not, Negate, BitNot
