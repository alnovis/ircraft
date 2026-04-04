package io.alnovis.ircraft.core.ir

sealed trait Stmt

object Stmt:
  case class Eval(expr: Expr) extends Stmt
  case class Return(value: Option[Expr] = None) extends Stmt
  case class Let(name: String, letType: TypeExpr, init: Option[Expr] = None, mutable: Boolean = false) extends Stmt
  case class Assign(target: Expr, value: Expr) extends Stmt
  case class If(cond: Expr, thenBody: Body, elseBody: Option[Body] = None) extends Stmt
  case class While(cond: Expr, body: Body) extends Stmt
  case class ForEach(variable: String, varType: TypeExpr, iterable: Expr, body: Body) extends Stmt
  case class Switch(expr: Expr, cases: Vector[SwitchCase], default: Option[Body] = None) extends Stmt
  case class Match(expr: Expr, cases: Vector[MatchCase]) extends Stmt
  case class Throw(expr: Expr) extends Stmt
  case class Comment(text: String) extends Stmt
  case class TryCatch(
    tryBody: Body,
    catches: Vector[CatchClause],
    finallyBody: Option[Body] = None
  ) extends Stmt

case class SwitchCase(pattern: Expr, body: Body)

case class MatchCase(pattern: Pattern, guard: Option[Expr] = None, body: Body)

sealed trait Pattern

object Pattern:
  case class TypeTest(name: String, typeExpr: TypeExpr) extends Pattern   // case x: Foo =>
  case class Literal(value: Expr) extends Pattern                         // case 42 =>
  case class Binding(name: String) extends Pattern                        // case x =>
  case object Wildcard extends Pattern                                    // case _ =>

case class CatchClause(exType: TypeExpr, name: String, body: Body)

case class Body(stmts: Vector[Stmt])

object Body:
  val empty: Body = Body(Vector.empty)
  def of(stmts: Stmt*): Body = Body(stmts.toVector)
