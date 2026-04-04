package io.alnovis.ircraft.emitters.scala

import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emit.{LanguageSyntax, TypeMapping}

class ScalaSyntax(config: ScalaEmitterConfig) extends LanguageSyntax:

  private val isScala3 = config.scalaVersion == ScalaTarget.Scala3

  val fileExtension: String = "scala"
  val statementTerminator: String = ""

  def packageDecl(pkg: String): String = s"package $pkg"

  def typeSignature(vis: String, kind: TypeKind, name: String, typeParams: String, supertypes: Vector[String]): String =
    val keyword = kind match
      case TypeKind.Protocol  => "trait"
      case TypeKind.Abstract  => "abstract class"
      case TypeKind.Singleton => "object"
      case TypeKind.Sum       => if isScala3 then "enum" else "sealed trait"
      case _                  => "class"
    val ext = extendsClause(supertypes)
    s"${vis}$keyword $name$typeParams$ext"

  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String =
    config.enumStyle match
      case EnumStyle.Scala3Enum =>
        val ext = extendsClause(supertypes)
        val params = if hasValues then "(val value: Int)" else ""
        s"${vis}enum $name$params$ext"
      case EnumStyle.SealedTrait =>
        val ext = extendsClause(supertypes)
        if hasValues then s"${vis}sealed abstract class $name(val value: Int)$ext"
        else s"${vis}sealed trait $name$ext"

  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String =
    val stripped = stripEnumPrefix(name, enumName)
    val scalaName = toPascalCase(stripped)
    config.enumStyle match
      case EnumStyle.Scala3Enum =>
        if args.nonEmpty then s"case $scalaName extends $enumName$args"
        else s"case $scalaName"
      case EnumStyle.SealedTrait =>
        if args.nonEmpty then s"case object $scalaName extends $enumName$args"
        else s"case object $scalaName extends $enumName"

  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String =
    val keyword = if mutable then "var" else "val"
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"${vis}$keyword $name: $typeName$initStr"

  def constDecl(vis: String, typeName: String, name: String, value: String): String =
    s"${vis}val $name: $typeName = $value"

  def funcSignature(
    vis: String, modifiers: Set[FuncModifier], typeParams: String,
    returnType: String, name: String, params: String,
    isAbstract: Boolean, parentKind: TypeKind
  ): String =
    val overrideMod = if modifiers.contains(FuncModifier.Override) then "override " else ""
    val paramsStr = if params.isEmpty then "" else s"($params)"
    s"${vis}${overrideMod}def $name$typeParams$paramsStr: $returnType"

  def paramDecl(name: String, typeName: String): String = s"$name: $typeName"

  def visibility(v: Visibility): String = v match
    case Visibility.Public         => ""
    case Visibility.Private        => "private "
    case Visibility.Protected      => "protected "
    case Visibility.Internal       => "private[this] "
    case Visibility.PackagePrivate => "private "

  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String =
    if tps.isEmpty then ""
    else
      val params = tps.map { tp =>
        if tp.upperBounds.nonEmpty then s"${tp.name} <: ${tp.upperBounds.map(tm.typeName).mkString(" with ")}"
        else tp.name
      }
      s"[${params.mkString(", ")}]"

  def newExpr(typeName: String, args: String): String =
    if config.useNewKeyword then s"new $typeName($args)"
    else s"$typeName($args)"

  def castExpr(expr: String, typeName: String): String = s"$expr.asInstanceOf[$typeName]"

  def ternaryExpr(cond: String, t: String, f: String): String =
    if isScala3 then s"if $cond then $t else $f"
    else s"if ($cond) $t else $f"

  def lambdaExpr(params: String, body: String): String = s"$params => $body"

  val nullLiteral: String  = "null"
  val thisLiteral: String  = "this"
  val superLiteral: String = "super"

  def returnStmt(expr: String): String = expr  // Scala: last expression is return value
  def returnVoid: String = "()"
  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String =
    val keyword = if mutable then "var" else "val"
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"$keyword $name: $typeName$initStr"
  def assignStmt(target: String, value: String): String = s"$target = $value"
  def throwStmt(expr: String): String = s"throw $expr"
  def forEachHeader(varName: String, typeName: String, iterExpr: String): String =
    if isScala3 then s"for $varName <- $iterExpr do"
    else s"for ($varName <- $iterExpr)"

  def binOp(op: BinaryOp): String = op match
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

  def unaryOp(op: UnaryOp): String = op match
    case UnaryOp.Not    => "!"
    case UnaryOp.Negate => "-"
    case UnaryOp.BitNot => "~"

  val supportsNativeMatch: Boolean = true
  def matchHeader(expr: String): String = s"$expr match"
  def matchCaseHeader(pattern: String): String = s"case $pattern =>"
  def patternTypeTest(name: String, typeName: String): String = s"$name: $typeName"
  def patternWildcard: String = "_"
  def patternLiteral(value: String): String = value

  // -- Naming conventions --

  // Standard method names that should never be transformed
  private val preservedNames = Set("toString", "hashCode", "equals", "clone", "finalize", "getClass", "notify", "notifyAll", "wait")

  override def transformMethodName(name: String): String =
    if preservedNames.contains(name) then name
    else name match
      case s if s.startsWith("get") && s.length > 3 =>
        toCamelCase(s.drop(3))
      case s if s.startsWith("has") && s.length > 3 =>
        "has" + toPascalCase(s.drop(3))
      case s if s.startsWith("is") && s.length > 2 =>
        s
      case s =>
        toCamelCase(s)

  override def transformFieldName(name: String): String = toCamelCase(name)

  override val useFuncEqualsStyle: Boolean = true

  // -- Helpers --

  private def extendsClause(supertypes: Vector[String]): String =
    if supertypes.isEmpty then ""
    else s" extends ${supertypes.mkString(" with ")}"

  /** UPPER_SNAKE_CASE -> PascalCase. */
  private def toPascalCase(s: String): String =
    s.toLowerCase.split("_").filter(_.nonEmpty).map(w => w.head.toUpper +: w.tail).mkString

  /** snake_case or PascalCase -> camelCase. */
  private def toCamelCase(s: String): String =
    if s.contains("_") then
      val parts = s.toLowerCase.split("_").filter(_.nonEmpty)
      if parts.isEmpty then s
      else parts.head + parts.tail.map(w => w.head.toUpper +: w.tail).mkString
    else if s.nonEmpty && s.head.isUpper then
      s.head.toLower +: s.tail
    else s

  /** Strip enum name prefix from variant name. E.g., "TEST_ENUM_UNKNOWN" with enum "TestEnum" -> "UNKNOWN". */
  private def stripEnumPrefix(variantName: String, enumName: String): String =
    // convert enum PascalCase to UPPER_SNAKE prefix: "TestEnum" -> "TEST_ENUM_"
    val prefix = enumName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase + "_"
    if variantName.startsWith(prefix) then variantName.drop(prefix.length)
    else variantName
