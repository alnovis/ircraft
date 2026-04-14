package io.alnovis.ircraft.emitters.java

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.emit.{ LanguageSyntax, TypeMapping }

/**
  * Java-specific syntax rules for the code emitter.
  *
  * Implements [[LanguageSyntax]] with Java conventions: semicolon-terminated statements,
  * `.java` file extension, C-style type declarations, `new` keyword for instantiation,
  * C-style casts, and standard Java visibility modifiers.
  *
  * Java does not support native pattern matching (pre-21), so match blocks are
  * desugared to if/else chains by [[BaseEmitter]].
  *
  * @see [[LanguageSyntax]] for the full syntax contract
  * @see [[io.alnovis.ircraft.emitters.scala.ScalaSyntax]] for the Scala counterpart
  */
object JavaSyntax extends LanguageSyntax {

  /** @return `"java"` */
  val fileExtension: String = "java"

  /** @return `";"` */
  val statementTerminator: String = ";"

  /**
    * Formats a Java package declaration.
    *
    * @param pkg the fully qualified package name
    * @return the package declaration (e.g., `"package com.example"`)
    */
  def packageDecl(pkg: String): String = s"package $pkg"

  /**
    * Constructs a Java type signature.
    *
    * Maps IR type kinds to Java keywords:
    *  - `Protocol` -> `interface`
    *  - `Abstract` -> `abstract class`
    *  - `Singleton` -> `final class`
    *  - others -> `class`
    *
    * Interfaces use `extends` for supertypes. Classes use `extends` for the first
    * supertype and `implements` for subsequent ones.
    *
    * @param vis        the visibility modifier string
    * @param kind       the IR type kind
    * @param name       the type name
    * @param typeParams the formatted type parameter list
    * @param supertypes the supertype names
    * @return the complete Java type signature
    */
  def typeSignature(
    vis: String,
    kind: TypeKind,
    name: String,
    typeParams: String,
    supertypes: Vector[String]
  ): String = {
    val (keyword, kindMod) = kind match {
      case TypeKind.Protocol  => ("interface", "")
      case TypeKind.Abstract  => ("class", "abstract ")
      case TypeKind.Singleton => ("class", "final ")
      case _                  => ("class", "")
    }
    val ext = kind match {
      case TypeKind.Protocol =>
        if (supertypes.nonEmpty) s" extends ${supertypes.mkString(", ")}" else ""
      case _ =>
        val (superclass, ifaces) = supertypes.splitAt(1)
        val extStr               = superclass.headOption.map(s => s" extends $s").getOrElse("")
        val implStr              = if (ifaces.nonEmpty) s" implements ${ifaces.mkString(", ")}" else ""
        extStr + implStr
    }
    s"${vis}${kindMod}$keyword $name$typeParams$ext"
  }

  /**
    * Constructs a Java enum signature.
    *
    * @param vis        the visibility modifier string
    * @param name       the enum name
    * @param supertypes the implemented interface names
    * @param hasValues  whether enum variants carry constructor arguments
    * @return the Java enum signature (e.g., `"public enum Color implements Serializable"`)
    */
  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String = {
    val impl = if (supertypes.nonEmpty) s" implements ${supertypes.mkString(", ")}" else ""
    s"${vis}enum $name$impl"
  }

  /**
    * Formats a Java enum constant.
    *
    * Uses a comma separator between constants and a semicolon after the last one.
    *
    * @param name     the constant name
    * @param args     the constructor arguments (e.g., `"(1)"`) or empty
    * @param isLast   whether this is the last constant
    * @param enumName the parent enum name (unused in Java)
    * @return the formatted enum constant line
    */
  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String = {
    val suffix = if (isLast) ";" else ","
    s"$name$args$suffix"
  }

  /**
    * Formats a Java field declaration.
    *
    * Non-mutable fields are declared with the `final` modifier.
    *
    * @param vis      the visibility modifier string
    * @param mutable  whether the field is mutable
    * @param typeName the field's type name
    * @param name     the field name
    * @param init     an optional initializer expression
    * @return the field declaration (e.g., `"private final String name = \"default\";"`)
    */
  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val fin     = if (!mutable) "final " else ""
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"${vis}${fin}$typeName $name$initStr;"
  }

  /**
    * Formats a Java constant declaration.
    *
    * Constants are declared as `static final`.
    *
    * @param vis      the visibility modifier string
    * @param typeName the constant's type name
    * @param name     the constant name
    * @param value    the constant value expression
    * @return the constant declaration (e.g., `"public static final int MAX = 100;"`)
    */
  def constDecl(vis: String, typeName: String, name: String, value: String): String =
    s"${vis}static final $typeName $name = $value;"

  /**
    * Constructs a Java method signature.
    *
    * Handles `static`, `abstract`, and `default` modifiers based on the function
    * modifiers and the enclosing type kind.
    *
    * @param vis        the visibility modifier string
    * @param modifiers  the function modifier set
    * @param typeParams the formatted type parameter list
    * @param returnType the return type name
    * @param name       the method name
    * @param params     the formatted parameter list
    * @param isAbstract whether the method has no body
    * @param parentKind the enclosing type kind (affects `default` modifier for interfaces)
    * @return the complete method signature
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
    val staticMod = if (modifiers.contains(FuncModifier.Static)) "static " else ""
    val isDefault = modifiers.contains(FuncModifier.Default) && !isAbstract && parentKind == TypeKind.Protocol
    if (isAbstract && parentKind != TypeKind.Protocol) s"${vis}abstract $returnType$typeParams $name($params)"
    else if (isDefault) s"${vis}default $returnType$typeParams $name($params)"
    else s"${vis}${staticMod}$returnType$typeParams $name($params)"
  }

  /**
    * Formats a Java parameter declaration in `type name` order.
    *
    * @param name     the parameter name
    * @param typeName the parameter's type name
    * @return the parameter declaration (e.g., `"String name"`)
    */
  def paramDecl(name: String, typeName: String): String = s"$typeName $name"

  /**
    * Converts IR visibility to a Java access modifier.
    *
    * `Internal` and `PackagePrivate` both map to package-private (empty string) in Java.
    *
    * @param v the IR visibility
    * @return the Java access modifier with trailing space, or empty string for package-private
    */
  def visibility(v: Visibility): String = v match {
    case Visibility.Public         => "public "
    case Visibility.Private        => "private "
    case Visibility.Protected      => "protected "
    case Visibility.Internal       => ""
    case Visibility.PackagePrivate => ""
  }

  /**
    * Formats Java generic type parameters with optional upper bounds.
    *
    * @param tps the type parameter definitions
    * @param tm  the type mapping for formatting bound types
    * @return the type parameter list (e.g., `"<T extends Comparable<T>>"`) or empty string
    */
  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String =
    if (tps.isEmpty) ""
    else {
      val params = tps.map { tp =>
        if (tp.upperBounds.nonEmpty) s"${tp.name} extends ${tp.upperBounds.map(tm.typeName).mkString(" & ")}"
        else tp.name
      }
      s"<${params.mkString(", ")}>"
    }

  /**
    * Formats a Java `new` expression.
    *
    * @param typeName the type to instantiate
    * @param args     the constructor arguments
    * @return the new expression (e.g., `"new ArrayList()"`)
    */
  def newExpr(typeName: String, args: String): String = s"new $typeName($args)"

  /**
    * Formats a Java cast expression with double parentheses.
    *
    * @param expr     the expression to cast
    * @param typeName the target type
    * @return the cast expression (e.g., `"((String) obj)"`)
    */
  def castExpr(expr: String, typeName: String): String = s"(($typeName) $expr)"

  /**
    * Formats a Java ternary expression.
    *
    * @param cond the condition
    * @param t    the true-branch value
    * @param f    the false-branch value
    * @return the ternary expression (e.g., `"(x > 0 ? x : -x)"`)
    */
  def ternaryExpr(cond: String, t: String, f: String): String = s"($cond ? $t : $f)"

  /**
    * Formats a Java lambda expression using arrow syntax.
    *
    * @param params the parameter names
    * @param body   the lambda body
    * @return the lambda expression (e.g., `"(x) -> x + 1"`)
    */
  def lambdaExpr(params: String, body: String): String = s"($params) -> $body"

  /** @return `"null"` */
  val nullLiteral: String = "null"

  /** @return `"this"` */
  val thisLiteral: String = "this"

  /** @return `"super"` */
  val superLiteral: String = "super"

  /**
    * Formats a Java return statement with semicolon.
    *
    * @param expr the expression to return
    * @return the return statement (e.g., `"return expr;"`)
    */
  def returnStmt(expr: String): String = s"return $expr;"

  /** @return `"return;"` */
  def returnVoid: String = "return;"

  /**
    * Formats a Java local variable declaration.
    *
    * Non-mutable variables are declared with the `final` modifier.
    *
    * @param mutable  whether the variable is mutable
    * @param typeName the variable's type name
    * @param name     the variable name
    * @param init     an optional initializer
    * @return the variable declaration (e.g., `"final String x = \"hello\";"`)
    */
  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val fin     = if (!mutable) "final " else ""
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"$fin$typeName $name$initStr;"
  }

  /**
    * Formats a Java assignment statement.
    *
    * @param target the assignment target
    * @param value  the value to assign
    * @return the assignment (e.g., `"x = 42;"`)
    */
  def assignStmt(target: String, value: String): String = s"$target = $value;"

  /**
    * Formats a Java throw statement.
    *
    * @param expr the exception expression
    * @return the throw statement (e.g., `"throw new RuntimeException();"`)
    */
  def throwStmt(expr: String): String = s"throw $expr;"

  /**
    * Formats a Java enhanced for-loop header.
    *
    * @param varName  the loop variable name
    * @param typeName the loop variable type
    * @param iterExpr the iterable expression
    * @return the for-each header (e.g., `"for (String s : list)"`)
    */
  def forEachHeader(varName: String, typeName: String, iterExpr: String): String =
    s"for ($typeName $varName : $iterExpr)"

  /**
    * Converts IR binary operators to Java operator symbols.
    *
    * @param op the IR binary operator
    * @return the Java operator symbol
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
    * Converts IR unary operators to Java operator symbols.
    *
    * @param op the IR unary operator
    * @return the Java operator symbol
    */
  def unaryOp(op: UnaryOp): String = op match {
    case UnaryOp.Not    => "!"
    case UnaryOp.Negate => "-"
    case UnaryOp.BitNot => "~"
  }

  /**
    * Java does not have native pattern matching (pre-21); match blocks are rendered as if-chains.
    *
    * @return `false`
    */
  val supportsNativeMatch: Boolean = false

  /** @return empty string (unused for Java since `supportsNativeMatch` is `false`) */
  def matchHeader(expr: String): String = ""

  /** @return empty string (unused for Java) */
  def matchCaseHeader(pattern: String): String = ""

  /**
    * Formats a Java instanceof type test.
    *
    * @param name     the binding variable name
    * @param typeName the type to test against
    * @return the instanceof expression (e.g., `"obj instanceof String"`)
    */
  def patternTypeTest(name: String, typeName: String): String = s"$name instanceof $typeName"

  /** @return `"default"` */
  def patternWildcard: String = "default"

  /**
    * Returns the literal value as-is.
    *
    * @param value the literal value string
    * @return the value unchanged
    */
  def patternLiteral(value: String): String = value
}
