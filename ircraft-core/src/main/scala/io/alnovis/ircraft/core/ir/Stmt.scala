package io.alnovis.ircraft.core.ir

/**
  * Statement AST for the ircraft IR.
  *
  * [[Stmt]] represents all statement-level constructs in the semantic IR,
  * including expression evaluation, variable declarations, assignments,
  * control flow (if/while/for/switch/match), exception handling, and returns.
  *
  * Statements are contained within [[Body]] instances, which in turn appear in
  * [[Func.body]], [[Expr.Lambda]], and control flow constructs.
  *
  * @see [[Expr]] for expression-level constructs
  * @see [[Body]] for the statement container
  * @see [[Pattern]] for match patterns
  */
sealed trait Stmt

/**
  * Companion object containing all [[Stmt]] variants.
  */
object Stmt {

  /**
    * An expression evaluated as a statement (its result is discarded).
    *
    * @param expr the expression to evaluate
    */
  case class Eval(expr: Expr) extends Stmt

  /**
    * A return statement, optionally carrying a value.
    *
    * @param value the optional return value expression
    */
  case class Return(value: Option[Expr] = None) extends Stmt

  /**
    * A local variable declaration.
    *
    * @param name    the variable name
    * @param letType the declared type
    * @param init    an optional initializer expression
    * @param mutable whether the variable is mutable (`true` = var, `false` = val)
    */
  case class Let(name: String, letType: TypeExpr, init: Option[Expr] = None, mutable: Boolean = false) extends Stmt

  /**
    * An assignment to an existing target (variable, field, or indexed expression).
    *
    * @param target the assignment target expression (must be an l-value)
    * @param value  the value to assign
    */
  case class Assign(target: Expr, value: Expr) extends Stmt

  /**
    * An if-else conditional statement.
    *
    * @param cond     the condition expression
    * @param thenBody the body executed when the condition is true
    * @param elseBody an optional body executed when the condition is false
    */
  case class If(cond: Expr, thenBody: Body, elseBody: Option[Body] = None) extends Stmt

  /**
    * A while-loop statement.
    *
    * @param cond the loop condition expression
    * @param body the loop body
    */
  case class While(cond: Expr, body: Body) extends Stmt

  /**
    * A for-each loop statement over an iterable.
    *
    * @param variable the loop variable name
    * @param varType  the loop variable type
    * @param iterable the expression producing the iterable
    * @param body     the loop body
    */
  case class ForEach(variable: String, varType: TypeExpr, iterable: Expr, body: Body) extends Stmt

  /**
    * A switch statement with typed cases and an optional default branch.
    *
    * @param expr    the expression to switch on
    * @param cases   the switch cases
    * @param default an optional default body
    * @see [[SwitchCase]] for individual case definitions
    */
  case class Switch(expr: Expr, cases: Vector[SwitchCase], default: Option[Body] = None) extends Stmt

  /**
    * A pattern-match statement.
    *
    * @param expr  the expression to match on
    * @param cases the match cases
    * @see [[MatchCase]] for individual case definitions
    * @see [[Pattern]] for the pattern ADT
    */
  case class Match(expr: Expr, cases: Vector[MatchCase]) extends Stmt

  /**
    * A throw statement.
    *
    * @param expr the exception expression to throw
    */
  case class Throw(expr: Expr) extends Stmt

  /**
    * A comment rendered as a statement in generated code.
    *
    * @param text the comment text
    */
  case class Comment(text: String) extends Stmt

  /**
    * A try-catch-finally statement.
    *
    * @param tryBody     the body of the try block
    * @param catches     the catch clauses
    * @param finallyBody an optional finally block body
    * @see [[CatchClause]] for individual catch clause definitions
    */
  case class TryCatch(
    tryBody: Body,
    catches: Vector[CatchClause],
    finallyBody: Option[Body] = None
  ) extends Stmt
}

/**
  * A case within a [[Stmt.Switch]] statement.
  *
  * @param pattern the pattern expression to match against
  * @param body    the body to execute when matched
  */
case class SwitchCase(pattern: Expr, body: Body)

/**
  * A case within a [[Stmt.Match]] statement.
  *
  * @param pattern the pattern to match against
  * @param guard   an optional guard expression (evaluated after pattern match)
  * @param body    the body to execute when matched
  * @see [[Pattern]] for the pattern ADT
  */
case class MatchCase(pattern: Pattern, guard: Option[Expr] = None, body: Body)

/**
  * Pattern ADT for [[Stmt.Match]] expressions.
  *
  * Supports type-test patterns, literal patterns, name-binding patterns,
  * and wildcards.
  *
  * @see [[MatchCase]] for the match case that contains patterns
  */
sealed trait Pattern

/**
  * Companion object containing all [[Pattern]] variants.
  */
object Pattern {

  /**
    * A type-test pattern that binds a name if the expression matches the given type.
    *
    * {{{
    * // case s: String => ...
    * Pattern.TypeTest("s", TypeExpr.STR)
    * }}}
    *
    * @param name     the binding name
    * @param typeExpr the type to test against
    */
  case class TypeTest(name: String, typeExpr: TypeExpr) extends Pattern

  /**
    * A literal value pattern.
    *
    * @param value the literal expression to match against
    */
  case class Literal(value: Expr) extends Pattern

  /**
    * A name-binding pattern that matches anything and binds it to a name.
    *
    * @param name the binding name
    */
  case class Binding(name: String) extends Pattern

  /** A wildcard pattern that matches anything without binding (`_`). */
  case object Wildcard extends Pattern
}

/**
  * A catch clause within a [[Stmt.TryCatch]] statement.
  *
  * @param exType the exception type to catch
  * @param name   the variable name to bind the caught exception
  * @param body   the body to execute when this exception type is caught
  */
case class CatchClause(exType: TypeExpr, name: String, body: Body)

/**
  * A sequence of statements forming a block body.
  *
  * Used as the body of functions, loops, conditionals, catch clauses, and lambdas.
  *
  * {{{
  * val body = Body.of(
  *   Stmt.Let("x", TypeExpr.INT, Some(Expr.Lit("1", TypeExpr.INT))),
  *   Stmt.Return(Some(Expr.Ref("x")))
  * )
  * }}}
  *
  * @param stmts the ordered sequence of statements
  * @see [[Body.empty]] for an empty body
  * @see [[Body.of]] for varargs construction
  */
case class Body(stmts: Vector[Stmt])

/**
  * Companion object for [[Body]], providing factory methods.
  */
object Body {

  /** An empty body containing no statements. */
  val empty: Body = Body(Vector.empty)

  /**
    * Creates a body from the given statements.
    *
    * @param stmts the statements to include
    * @return a new [[Body]] containing the given statements
    */
  def of(stmts: Stmt*): Body = Body(stmts.toVector)
}
