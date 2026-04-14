package io.alnovis.ircraft.emitters.scala

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.emit.{ LanguageSyntax, TypeMapping }

/**
  * Scala-specific syntax rules for the code emitter.
  *
  * Implements [[LanguageSyntax]] with Scala conventions: no statement terminators,
  * `.scala` file extension, `val`/`var` field declarations, `def` function signatures
  * with expression-style bodies, `with`-separated supertypes, and native pattern matching.
  *
  * Adapts to the configured [[ScalaTarget]] (Scala 2 vs Scala 3) for differences like:
  *  - Ternary expressions: `if x then a else b` (Scala 3) vs `if (x) a else b` (Scala 2)
  *  - For-comprehensions: `for x <- xs do` (Scala 3) vs `for (x <- xs)` (Scala 2)
  *  - Enum declarations: native `enum` (Scala 3) vs sealed trait (Scala 2)
  *
  * Also applies Scala naming conventions: stripping `get` prefixes from public methods
  * and converting `snake_case` names to `camelCase`.
  *
  * @param config the [[ScalaEmitterConfig]] controlling version and enum style
  *
  * @see [[LanguageSyntax]] for the full syntax contract
  * @see [[ScalaEmitterConfig]] for configuration options
  * @see [[io.alnovis.ircraft.emitters.java.JavaSyntax]] for the Java counterpart
  */
class ScalaSyntax(config: ScalaEmitterConfig) extends LanguageSyntax {

  private val isScala3 = config.scalaVersion == ScalaTarget.Scala3

  /** @return `"scala"` */
  val fileExtension: String = "scala"

  /** @return `""` (Scala does not use statement terminators) */
  val statementTerminator: String = ""

  /**
    * Formats a Scala package declaration.
    *
    * @param pkg the fully qualified package name
    * @return the package declaration (e.g., `"package com.example"`)
    */
  def packageDecl(pkg: String): String = s"package $pkg"

  /**
    * Constructs a Scala type signature.
    *
    * Maps IR type kinds to Scala keywords:
    *  - `Protocol` -> `trait`
    *  - `Abstract` -> `abstract class`
    *  - `Singleton` -> `object`
    *  - `Sum` -> `enum` (Scala 3) or `sealed trait` (Scala 2)
    *  - others -> `class`
    *
    * Supertypes are joined with `with` in the `extends` clause.
    *
    * @param vis        the visibility modifier string
    * @param kind       the IR type kind
    * @param name       the type name
    * @param typeParams the formatted type parameter list
    * @param supertypes the supertype names
    * @return the complete Scala type signature
    */
  def typeSignature(
    vis: String,
    kind: TypeKind,
    name: String,
    typeParams: String,
    supertypes: Vector[String]
  ): String = {
    val keyword = kind match {
      case TypeKind.Protocol  => "trait"
      case TypeKind.Abstract  => "abstract class"
      case TypeKind.Singleton => "object"
      case TypeKind.Sum       => if (isScala3) "enum" else "sealed trait"
      case _                  => "class"
    }
    val ext = extendsClause(supertypes)
    s"${vis}$keyword $name$typeParams$ext"
  }

  /**
    * Constructs a Scala enum signature.
    *
    * In Scala 3 mode, produces `enum Name(val value: Int)` for value-carrying enums.
    * In Scala 2 mode, produces `sealed abstract class Name(val value: Int)` or
    * `sealed trait Name`.
    *
    * @param vis        the visibility modifier string
    * @param name       the enum name
    * @param supertypes the extended trait/class names
    * @param hasValues  whether the enum variants carry constructor arguments
    * @return the enum signature string
    */
  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String =
    config.enumStyle match {
      case EnumStyle.Scala3Enum =>
        val ext    = extendsClause(supertypes)
        val params = if (hasValues) "(val value: Int)" else ""
        s"${vis}enum $name$params$ext"
      case EnumStyle.SealedTrait =>
        val ext = extendsClause(supertypes)
        if (hasValues) s"${vis}sealed abstract class $name(val value: Int)$ext"
        else s"${vis}sealed trait $name$ext"
    }

  /**
    * Formats a Scala enum variant.
    *
    * In Scala 3 mode: `case Variant` or `case Variant extends EnumName(args)`.
    * In Scala 2 mode: `case object Variant extends EnumName` (with optional args).
    *
    * The variant name is converted from `UPPER_SNAKE_CASE` to `PascalCase`, and
    * the enum name prefix is stripped if present.
    *
    * @param name     the variant name (typically in `UPPER_SNAKE_CASE`)
    * @param args     the constructor arguments or empty string
    * @param isLast   whether this is the last variant (unused in Scala)
    * @param enumName the parent enum name, used for extends clause and prefix stripping
    * @return the formatted enum variant line
    */
  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String = {
    val stripped  = stripEnumPrefix(name, enumName)
    val scalaName = toPascalCase(stripped)
    config.enumStyle match {
      case EnumStyle.Scala3Enum =>
        if (args.nonEmpty) s"case $scalaName extends $enumName$args"
        else s"case $scalaName"
      case EnumStyle.SealedTrait =>
        if (args.nonEmpty) s"case object $scalaName extends $enumName$args"
        else s"case object $scalaName extends $enumName"
    }
  }

  /**
    * Formats a Scala field declaration using `val` or `var`.
    *
    * @param vis      the visibility modifier string
    * @param mutable  whether the field is mutable (`var` vs `val`)
    * @param typeName the field's type name
    * @param name     the field name
    * @param init     an optional initializer expression
    * @return the field declaration (e.g., `"val name: String = \"default\""`)
    */
  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val keyword = if (mutable) "var" else "val"
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"${vis}$keyword $name: $typeName$initStr"
  }

  /**
    * Formats a Scala constant declaration using `val`.
    *
    * @param vis      the visibility modifier string
    * @param typeName the constant's type name
    * @param name     the constant name
    * @param value    the value expression
    * @return the constant declaration (e.g., `"val MaxSize: Int = 100"`)
    */
  def constDecl(vis: String, typeName: String, name: String, value: String): String =
    s"${vis}val $name: $typeName = $value"

  /**
    * Formats a Scala type alias declaration.
    *
    * @param vis    the visibility modifier string
    * @param name   the alias name
    * @param target the target type name
    * @return `Some(declaration)` always (Scala supports type aliases)
    */
  override def aliasDecl(vis: String, name: String, target: String): Option[String] =
    Some(s"${vis}type $name = $target")

  /**
    * Constructs a Scala method signature using `def` keyword.
    *
    * Includes `override` modifier when present. Omits parentheses for
    * no-parameter methods.
    *
    * @param vis        the visibility modifier string
    * @param modifiers  the function modifier set
    * @param typeParams the formatted type parameter list
    * @param returnType the return type name
    * @param name       the method name
    * @param params     the formatted parameter list
    * @param isAbstract whether the method has no body (unused; Scala infers abstractness)
    * @param parentKind the enclosing type kind (unused in Scala)
    * @return the method signature (e.g., `"def name[A](x: Int): String"`)
    */
  def funcSignature(
    vis: String,
    modifiers: Set[FuncModifier],
    typeParams: String,
    returnType: String,
    name: String,
    params: String,
    isAbstract: Boolean,
    parentKind: TypeKind
  ): String = {
    val overrideMod = if (modifiers.contains(FuncModifier.Override)) "override " else ""
    val paramsStr   = if (params.isEmpty) "" else s"($params)"
    s"${vis}${overrideMod}def $name$typeParams$paramsStr: $returnType"
  }

  /**
    * Formats a Scala parameter declaration in `name: Type` order.
    *
    * @param name     the parameter name
    * @param typeName the parameter's type name
    * @return the parameter declaration (e.g., `"name: String"`)
    */
  def paramDecl(name: String, typeName: String): String = s"$name: $typeName"

  /**
    * Converts IR visibility to Scala access modifiers.
    *
    * `Public` maps to empty string (Scala default). `Internal` maps to `private[this]`.
    * `PackagePrivate` maps to `private`.
    *
    * @param v the IR visibility level
    * @return the Scala access modifier with trailing space, or empty string for public
    */
  def visibility(v: Visibility): String = v match {
    case Visibility.Public         => ""
    case Visibility.Private        => "private "
    case Visibility.Protected      => "protected "
    case Visibility.Internal       => "private[this] "
    case Visibility.PackagePrivate => "private "
  }

  /**
    * Formats Scala type parameters with optional upper bounds using `<:`.
    *
    * Multiple bounds are joined with `with`.
    *
    * @param tps the type parameter definitions
    * @param tm  the type mapping for formatting bound types
    * @return the type parameter list (e.g., `"[A <: Ordered[A]]"`) or empty string
    */
  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String =
    if (tps.isEmpty) ""
    else {
      val params = tps.map { tp =>
        if (tp.upperBounds.nonEmpty) s"${tp.name} <: ${tp.upperBounds.map(tm.typeName).mkString(" with ")}"
        else tp.name
      }
      s"[${params.mkString(", ")}]"
    }

  /**
    * Formats a Scala constructor expression.
    *
    * Uses `new` keyword if [[ScalaEmitterConfig.useNewKeyword]] is `true` (Scala 2 style),
    * otherwise uses apply-style `TypeName(args)` (Scala 3 / case class style).
    *
    * @param typeName the type to instantiate
    * @param args     the constructor arguments
    * @return the constructor expression
    */
  def newExpr(typeName: String, args: String): String =
    if (config.useNewKeyword) s"new $typeName($args)"
    else s"$typeName($args)"

  /**
    * Formats a Scala type cast expression using `asInstanceOf`.
    *
    * @param expr     the expression to cast
    * @param typeName the target type
    * @return the cast expression (e.g., `"expr.asInstanceOf[String]"`)
    */
  def castExpr(expr: String, typeName: String): String = s"$expr.asInstanceOf[$typeName]"

  /**
    * Formats a Scala conditional expression.
    *
    * Uses `if ... then ... else ...` for Scala 3 and `if (...) ... else ...` for Scala 2.
    *
    * @param cond the condition
    * @param t    the true-branch value
    * @param f    the false-branch value
    * @return the conditional expression string
    */
  def ternaryExpr(cond: String, t: String, f: String): String =
    if (isScala3) s"if $cond then $t else $f"
    else s"if ($cond) $t else $f"

  /**
    * Formats a Scala lambda expression using `=>`.
    *
    * @param params the parameter names
    * @param body   the lambda body
    * @return the lambda expression (e.g., `"x => x + 1"`)
    */
  def lambdaExpr(params: String, body: String): String = s"$params => $body"

  /** @return `"null"` */
  val nullLiteral: String = "null"

  /** @return `"this"` */
  val thisLiteral: String = "this"

  /** @return `"super"` */
  val superLiteral: String = "super"

  /**
    * In Scala, the last expression is the return value; no `return` keyword is used.
    *
    * @param expr the expression to return
    * @return the expression itself
    */
  def returnStmt(expr: String): String = expr

  /** @return `"()"` (Scala Unit literal) */
  def returnVoid: String = "()"

  /**
    * Formats a Scala local variable declaration.
    *
    * @param mutable  whether the variable is mutable (`var` vs `val`)
    * @param typeName the variable's type name
    * @param name     the variable name
    * @param init     an optional initializer
    * @return the variable declaration (e.g., `"val x: String = \"hello\""`)
    */
  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val keyword = if (mutable) "var" else "val"
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"$keyword $name: $typeName$initStr"
  }

  /**
    * Formats a Scala assignment (no semicolon).
    *
    * @param target the assignment target
    * @param value  the value to assign
    * @return the assignment (e.g., `"x = 42"`)
    */
  def assignStmt(target: String, value: String): String = s"$target = $value"

  /**
    * Formats a Scala throw expression (no semicolon).
    *
    * @param expr the exception expression
    * @return the throw expression (e.g., `"throw new RuntimeException()"`)
    */
  def throwStmt(expr: String): String = s"throw $expr"

  /**
    * Formats a Scala for-comprehension header.
    *
    * Uses `for x <- xs do` for Scala 3, `for (x <- xs)` for Scala 2.
    *
    * @param varName  the loop variable name
    * @param typeName the loop variable type (unused; Scala infers the type)
    * @param iterExpr the iterable expression
    * @return the for-comprehension header string
    */
  def forEachHeader(varName: String, typeName: String, iterExpr: String): String =
    if (isScala3) s"for $varName <- $iterExpr do"
    else s"for ($varName <- $iterExpr)"

  /**
    * Converts IR binary operators to Scala operator symbols.
    *
    * @param op the IR binary operator
    * @return the Scala operator symbol
    */
  def binOp(op: BinaryOp): String = op match {
    case BinaryOp.Eq     => "=="
    case BinaryOp.Neq    => "!="
    case BinaryOp.Lt     => "<"
    case BinaryOp.Gt     => ">"
    case BinaryOp.Lte    => "<="
    case BinaryOp.Gte    => ">="
    case BinaryOp.Add    => "+"
    case BinaryOp.Sub    => "-"
    case BinaryOp.Mul    => "*"
    case BinaryOp.Div    => "/"
    case BinaryOp.Mod    => "%"
    case BinaryOp.And    => "&&"
    case BinaryOp.Or     => "||"
    case BinaryOp.BitAnd => "&"
    case BinaryOp.BitOr  => "|"
    case BinaryOp.BitXor => "^"
  }

  /**
    * Converts IR unary operators to Scala operator symbols.
    *
    * @param op the IR unary operator
    * @return the Scala operator symbol
    */
  def unaryOp(op: UnaryOp): String = op match {
    case UnaryOp.Not    => "!"
    case UnaryOp.Negate => "-"
    case UnaryOp.BitNot => "~"
  }

  /**
    * Scala supports native pattern matching.
    *
    * @return `true`
    */
  val supportsNativeMatch: Boolean = true

  /**
    * Formats a Scala match expression header.
    *
    * @param expr the expression to match on
    * @return the match header (e.g., `"x match"`)
    */
  def matchHeader(expr: String): String = s"$expr match"

  /**
    * Formats a Scala match case header.
    *
    * @param pattern the pattern with optional guard
    * @return the case header (e.g., `"case x: Int =>"`)
    */
  def matchCaseHeader(pattern: String): String = s"case $pattern =>"

  /**
    * Formats a Scala type-test pattern.
    *
    * @param name     the binding variable name
    * @param typeName the type to test against
    * @return the type-test pattern (e.g., `"x: Int"`)
    */
  def patternTypeTest(name: String, typeName: String): String = s"$name: $typeName"

  /** @return `"_"` (Scala wildcard pattern) */
  def patternWildcard: String = "_"

  /**
    * Returns the literal value as-is.
    *
    * @param value the literal value string
    * @return the value unchanged
    */
  def patternLiteral(value: String): String = value

  // -- Naming conventions --

  /** Standard JVM method names that should never be transformed. */
  private val preservedNames =
    Set("toString", "hashCode", "equals", "clone", "finalize", "getClass", "notify", "notifyAll", "wait")

  /**
    * Transforms method names to Scala conventions.
    *
    * Applies the following rules (unless the name is in the preserved set):
    *  - `getName` -> `name` (strips `get` prefix, converts to camelCase)
    *  - `hasName` -> `hasName` (preserves `has` prefix)
    *  - `isValid` -> `isValid` (preserves `is` prefix)
    *  - Others -> converted to camelCase
    *
    * @param name the original method name
    * @return the Scala-idiomatic method name
    */
  override def transformMethodName(name: String): String =
    if (preservedNames.contains(name)) name
    else {
      name match {
        case s if s.startsWith("get") && s.length > 3 =>
          toCamelCase(s.drop(3))
        case s if s.startsWith("has") && s.length > 3 =>
          "has" + toPascalCase(s.drop(3))
        case s if s.startsWith("is") && s.length > 2 =>
          s
        case s =>
          toCamelCase(s)
      }
    }

  /**
    * Transforms field names to Scala camelCase convention.
    *
    * @param name the original field name (may be `snake_case`)
    * @return the camelCase field name
    */
  override def transformFieldName(name: String): String = toCamelCase(name)

  /**
    * Scala uses expression-style function bodies (`def x: Int = expr`).
    *
    * @return `true`
    */
  override val useFuncEqualsStyle: Boolean = true

  // -- Helpers --

  private def extendsClause(supertypes: Vector[String]): String =
    if (supertypes.isEmpty) ""
    else s" extends ${supertypes.mkString(" with ")}"

  /** Converts UPPER_SNAKE_CASE to PascalCase. */
  private def toPascalCase(s: String): String =
    s.toLowerCase.split("_").filter(_.nonEmpty).map(w => w.head.toUpper +: w.tail).mkString

  /** Converts snake_case or PascalCase to camelCase. */
  private def toCamelCase(s: String): String =
    if (s.contains("_")) {
      val parts = s.toLowerCase.split("_").filter(_.nonEmpty)
      if (parts.isEmpty) s
      else parts.head + parts.tail.map(w => w.head.toUpper +: w.tail).mkString
    } else if (s.nonEmpty && s.head.isUpper) s.head.toLower +: s.tail
    else s

  /** Strips enum name prefix from variant name. E.g., `"TEST_ENUM_UNKNOWN"` with enum `"TestEnum"` -> `"UNKNOWN"`. */
  private def stripEnumPrefix(variantName: String, enumName: String): String = {
    // convert enum PascalCase to UPPER_SNAKE prefix: "TestEnum" -> "TEST_ENUM_"
    val prefix = enumName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase + "_"
    if (variantName.startsWith(prefix)) variantName.drop(prefix.length)
    else variantName
  }
}
