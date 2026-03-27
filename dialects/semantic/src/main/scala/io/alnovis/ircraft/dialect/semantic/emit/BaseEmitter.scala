package io.alnovis.ircraft.dialect.semantic.emit

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.{ CommentStyle, Emitter, EmitterUtils }
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/**
  * Abstract base for language emitters operating on Semantic Dialect IR.
  *
  * Provides shared logic for file emission, import collection, expression emission, and operator formatting. Subclasses
  * implement language-specific hooks for type declarations, statements, and 5 expression forms that differ across
  * languages.
  */
abstract class BaseEmitter extends Emitter with EmitterUtils:

  // ── Required implementations ──────────────────────────────────────────

  /** Language type mapping (e.g., JavaTypeMapping, KotlinTypeMapping). */
  protected def tm: LanguageTypeMapping

  /** File extension without dot (e.g., "java", "kt", "scala"). */
  protected def fileExtension: String

  /** Documentation comment style. */
  protected def commentStyle: CommentStyle

  /** Statement terminator (";" for Java, "" for Kotlin/Scala). */
  protected def statementTerminator: String

  // ── Language-specific hooks (abstract) ─────────────────────────────────

  /** Emit a top-level type declaration (interface, class, enum). */
  protected def emitTypeDecl(op: Operation, level: Int): String

  /** Emit a single statement. */
  protected def emitStmt(s: Statement, level: Int): String

  /** Emit a type cast expression (e.g., `(Type) expr`, `expr as Type`, `expr.asInstanceOf[Type]`). */
  protected def emitCast(expr: Expression, t: TypeRef): String

  /** Emit a constructor call (e.g., `new Type(args)`, `Type(args)`). */
  protected def emitNewInstance(t: TypeRef, args: List[Expression]): String

  /** Emit a ternary/conditional (e.g., `c ? t : f`, `if (c) t else f`). */
  protected def emitConditional(cond: Expression, t: Expression, f: Expression): String

  /** Emit a unary operation (prefix for most, but Kotlin BitwiseNot is postfix). */
  protected def emitUnaryOp(op: UnOperator, expr: Expression): String

  /** Emit a lambda expression. */
  protected def emitLambda(params: List[String], body: Expression): String

  // ── Shared (final) ────────────────────────────────────────────────────

  /** Entry point: emit Module to Map[filePath, sourceCode]. */
  final def emit(module: Module): Map[String, String] =
    module.topLevel
      .collect { case f: FileOp => f }
      .flatMap: f =>
        f.types.map: op =>
          val path   = f.packageName.replace('.', '/') + s"/${typeOpName(op)}.$fileExtension"
          val source = emitSource(f.packageName, collectImports(op), op)
          path -> source
      .toMap

  /** Assemble a source file: package + imports + type declaration. */
  protected final def emitSource(pkg: String, imports: Set[String], op: Operation): String =
    val sb = StringBuilder()
    sb.append(s"package $pkg$statementTerminator\n")
    val sorted = imports.toList.sorted
    if sorted.nonEmpty then
      sb.append("\n")
      sorted.foreach(i => sb.append(s"import $i$statementTerminator\n"))
    sb.append("\n")
    sb.append(emitTypeDecl(op, 0))
    sb.append("\n")
    sb.result()

  /** Emit a block of statements. */
  protected final def emitBlock(b: Block, level: Int): String =
    b.statements.map(s => emitStmt(s, level)).mkString("\n")

  /** Emit an expression (shared core, calls hooks for language-specific forms). */
  protected final def emitExpr(e: Expression): String = e match
    case Expression.Literal(v, _)                  => v
    case Expression.Identifier(n)                  => n
    case Expression.MethodCall(recv, name, args, _) =>
      val r = recv.map(r => s"${emitExpr(r)}.").getOrElse("")
      s"$r$name(${args.map(emitExpr).mkString(", ")})"
    case Expression.FieldAccess(recv, name)         => s"${emitExpr(recv)}.$name"
    case Expression.NewInstance(t, args)             => emitNewInstance(t, args)
    case Expression.Cast(expr, t)                   => emitCast(expr, t)
    case Expression.Conditional(cond, t, f)         => emitConditional(cond, t, f)
    case Expression.BinaryOp(l, op, r)              => emitBinaryOp(l, op, r)
    case Expression.UnaryOp(op, expr)               => emitUnaryOp(op, expr)
    case Expression.Lambda(params, body)            => emitLambda(params, body)
    case Expression.NullLiteral                     => "null"
    case Expression.ThisRef                         => "this"
    case Expression.SuperRef                        => "super"

  /** Extract the type name from an operation. */
  protected final def typeOpName(op: Operation): String = op match
    case i: InterfaceOp => i.name
    case c: ClassOp     => c.name
    case e: EnumClassOp => e.name
    case _              => "Unknown"

  /** Collect import statements by walking the operation tree. */
  protected final def collectImports(op: Operation): Set[String] =
    val imports = scala.collection.mutable.Set.empty[String]
    def walk(o: Operation): Unit = o match
      case c: ClassOp =>
        c.superClass.foreach(t => imports ++= tm.importsFor(t))
        c.implementsTypes.foreach(t => imports ++= tm.importsFor(t))
        c.fields.foreach(f => imports ++= tm.importsFor(f.fieldType))
        c.constructors.foreach(ct => ct.parameters.foreach(p => imports ++= tm.importsFor(p.paramType)))
        c.methods.foreach(walk)
        c.nestedTypes.foreach(walk)
      case i: InterfaceOp =>
        i.extendsTypes.foreach(t => imports ++= tm.importsFor(t))
        i.methods.foreach(walk)
        i.nestedTypes.foreach(walk)
      case m: MethodOp =>
        imports ++= tm.importsFor(m.returnType)
        m.parameters.foreach(p => imports ++= tm.importsFor(p.paramType))
      case e: EnumClassOp =>
        e.implementsTypes.foreach(t => imports ++= tm.importsFor(t))
        e.fields.foreach(f => imports ++= tm.importsFor(f.fieldType))
        e.constructors.foreach(ct => ct.parameters.foreach(p => imports ++= tm.importsFor(p.paramType)))
        e.methods.foreach(walk)
      case _ => ()
    walk(op)
    imports.toSet

  // ── Shared with default (overridable) ─────────────────────────────────

  /** Emit a binary operation. Default: no parens. Override for Java (adds parens). */
  protected def emitBinaryOp(l: Expression, op: BinOperator, r: Expression): String =
    s"${emitExpr(l)} ${emitBinOp(op)} ${emitExpr(r)}"

  /** Emit a binary operator symbol. Default uses Java/Scala syntax. Override for Kotlin bitwise. */
  protected def emitBinOp(op: BinOperator): String = op match
    case BinOperator.Eq     => "=="
    case BinOperator.Neq    => "!="
    case BinOperator.Lt     => "<"
    case BinOperator.Gt     => ">"
    case BinOperator.Lte    => "<="
    case BinOperator.Gte    => ">="
    case BinOperator.Add    => "+"
    case BinOperator.Sub    => "-"
    case BinOperator.Mul    => "*"
    case BinOperator.Div    => "/"
    case BinOperator.Mod    => "%"
    case BinOperator.And    => "&&"
    case BinOperator.Or     => "||"
    case BinOperator.BitAnd => "&"
    case BinOperator.BitOr  => "|"
    case BinOperator.BitXor => "^"

