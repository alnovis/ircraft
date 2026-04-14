package io.alnovis.ircraft.core.ir

/**
  * Expression AST for the ircraft IR.
  *
  * [[Expr]] represents all expression-level constructs in the semantic IR,
  * including literals, references, field access, method calls, operators,
  * object construction, lambdas, casts, and ternary conditionals.
  *
  * Expressions are used in [[Field.defaultValue]], [[Param.defaultValue]],
  * [[Stmt]] nodes, and [[Annotation.args]].
  *
  * @see [[Stmt]] for statement-level constructs
  * @see [[BinaryOp]] for binary operator variants
  * @see [[UnaryOp]] for unary operator variants
  */
sealed trait Expr

/**
  * Companion object containing all [[Expr]] variants.
  */
object Expr {

  /**
    * A literal value with its type.
    *
    * The `value` is stored as a string representation that code generators
    * interpret according to `litType`.
    *
    * {{{
    * Expr.Lit("42", TypeExpr.INT)
    * Expr.Lit("hello", TypeExpr.STR)
    * }}}
    *
    * @param value   the string representation of the literal value
    * @param litType the type of the literal
    */
  case class Lit(value: String, litType: TypeExpr) extends Expr

  /**
    * A name reference (variable, parameter, or unqualified identifier).
    *
    * @param name the referenced identifier
    */
  case class Ref(name: String) extends Expr

  /** The `null` literal. */
  case object Null extends Expr

  /** A reference to `this` (the current instance). */
  case object This extends Expr

  /** A reference to `super` (the parent class). */
  case object Super extends Expr

  /**
    * A field or property access on an expression (dot access).
    *
    * {{{
    * Expr.Access(Expr.This, "name")  // this.name
    * }}}
    *
    * @param expr  the receiver expression
    * @param field the field name to access
    */
  case class Access(expr: Expr, field: String) extends Expr

  /**
    * An index access on an expression (bracket access).
    *
    * {{{
    * Expr.Index(Expr.Ref("arr"), Expr.Lit("0", TypeExpr.INT))  // arr[0]
    * }}}
    *
    * @param expr  the receiver expression (array, list, map, etc.)
    * @param index the index expression
    */
  case class Index(expr: Expr, index: Expr) extends Expr

  /**
    * A method or function call.
    *
    * Supports both qualified calls (with a receiver) and unqualified calls.
    * May include type arguments for generic method invocations.
    *
    * {{{
    * Expr.Call(Some(Expr.Ref("list")), "add", Vector(Expr.Ref("item")))
    * Expr.Call(None, "println", Vector(Expr.Lit("hello", TypeExpr.STR)))
    * }}}
    *
    * @param receiver an optional receiver expression (None for unqualified calls)
    * @param name     the method/function name
    * @param args     the argument expressions
    * @param typeArgs type arguments for generic calls
    */
  case class Call(
    receiver: Option[Expr],
    name: String,
    args: Vector[Expr] = Vector.empty,
    typeArgs: Vector[TypeExpr] = Vector.empty
  ) extends Expr

  /**
    * An object instantiation expression.
    *
    * {{{
    * Expr.New(TypeExpr.Named("ArrayList"), Vector.empty)
    * }}}
    *
    * @param typeExpr the type to instantiate
    * @param args     constructor arguments
    */
  case class New(typeExpr: TypeExpr, args: Vector[Expr] = Vector.empty) extends Expr

  /**
    * A binary operator expression.
    *
    * @param left  the left-hand operand
    * @param op    the binary operator
    * @param right the right-hand operand
    * @see [[BinaryOp]] for available operators
    */
  case class BinOp(left: Expr, op: BinaryOp, right: Expr) extends Expr

  /**
    * A unary operator expression.
    *
    * @param op   the unary operator
    * @param expr the operand
    * @see [[UnaryOp]] for available operators
    */
  case class UnOp(op: UnaryOp, expr: Expr) extends Expr

  /**
    * A ternary conditional expression (`cond ? ifTrue : ifFalse`).
    *
    * @param cond    the condition expression
    * @param ifTrue  the expression evaluated when `cond` is true
    * @param ifFalse the expression evaluated when `cond` is false
    */
  case class Ternary(cond: Expr, ifTrue: Expr, ifFalse: Expr) extends Expr

  /**
    * A type cast expression.
    *
    * @param expr   the expression to cast
    * @param target the target type
    */
  case class Cast(expr: Expr, target: TypeExpr) extends Expr

  /**
    * A lambda (anonymous function) expression.
    *
    * @param params the lambda parameter list
    * @param body   the lambda body
    * @see [[Param]] for parameter definitions
    * @see [[Body]] for the body representation
    */
  case class Lambda(params: Vector[Param], body: Body) extends Expr

  /**
    * A type reference used as an expression (e.g., `MyClass.class` or `typeof(MyClass)`).
    *
    * @param typeExpr the referenced type
    */
  case class TypeRef(typeExpr: TypeExpr) extends Expr
}

/**
  * Binary operators for [[Expr.BinOp]] expressions.
  *
  * Covers comparison, arithmetic, logical, and bitwise operators.
  *
  * @see [[Expr.BinOp]] for usage in expressions
  */
sealed abstract class BinaryOp

/** Companion containing all binary operator variants. */
object BinaryOp {

  /** Equality comparison (`==`). */
  case object Eq extends BinaryOp

  /** Inequality comparison (`!=`). */
  case object Neq extends BinaryOp

  /** Less-than comparison (`<`). */
  case object Lt extends BinaryOp

  /** Greater-than comparison (`>`). */
  case object Gt extends BinaryOp

  /** Less-than-or-equal comparison (`<=`). */
  case object Lte extends BinaryOp

  /** Greater-than-or-equal comparison (`>=`). */
  case object Gte extends BinaryOp

  /** Addition (`+`). */
  case object Add extends BinaryOp

  /** Subtraction (`-`). */
  case object Sub extends BinaryOp

  /** Multiplication (`*`). */
  case object Mul extends BinaryOp

  /** Division (`/`). */
  case object Div extends BinaryOp

  /** Modulo (`%`). */
  case object Mod extends BinaryOp

  /** Logical AND (`&&`). */
  case object And extends BinaryOp

  /** Logical OR (`||`). */
  case object Or extends BinaryOp

  /** Bitwise AND (`&`). */
  case object BitAnd extends BinaryOp

  /** Bitwise OR (`|`). */
  case object BitOr extends BinaryOp

  /** Bitwise XOR (`^`). */
  case object BitXor extends BinaryOp
}

/**
  * Unary operators for [[Expr.UnOp]] expressions.
  *
  * @see [[Expr.UnOp]] for usage in expressions
  */
sealed abstract class UnaryOp

/** Companion containing all unary operator variants. */
object UnaryOp {

  /** Logical NOT (`!`). */
  case object Not extends UnaryOp

  /** Arithmetic negation (`-`). */
  case object Negate extends UnaryOp

  /** Bitwise NOT (`~`). */
  case object BitNot extends UnaryOp
}
