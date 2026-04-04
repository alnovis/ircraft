package io.alnovis.ircraft.emitters.java

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.emit.{ LanguageSyntax, TypeMapping }

object JavaSyntax extends LanguageSyntax {

  val fileExtension: String       = "java"
  val statementTerminator: String = ";"

  def packageDecl(pkg: String): String = s"package $pkg"

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

  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String = {
    val impl = if (supertypes.nonEmpty) s" implements ${supertypes.mkString(", ")}" else ""
    s"${vis}enum $name$impl"
  }

  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String = {
    val suffix = if (isLast) ";" else ","
    s"$name$args$suffix"
  }

  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val fin     = if (!mutable) "final " else ""
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"${vis}${fin}$typeName $name$initStr;"
  }

  def constDecl(vis: String, typeName: String, name: String, value: String): String =
    s"${vis}static final $typeName $name = $value;"

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

  def paramDecl(name: String, typeName: String): String = s"$typeName $name"

  def visibility(v: Visibility): String = v match {
    case Visibility.Public         => "public "
    case Visibility.Private        => "private "
    case Visibility.Protected      => "protected "
    case Visibility.Internal       => ""
    case Visibility.PackagePrivate => ""
  }

  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String =
    if (tps.isEmpty) ""
    else {
      val params = tps.map { tp =>
        if (tp.upperBounds.nonEmpty) s"${tp.name} extends ${tp.upperBounds.map(tm.typeName).mkString(" & ")}"
        else tp.name
      }
      s"<${params.mkString(", ")}>"
    }

  def newExpr(typeName: String, args: String): String         = s"new $typeName($args)"
  def castExpr(expr: String, typeName: String): String        = s"(($typeName) $expr)"
  def ternaryExpr(cond: String, t: String, f: String): String = s"($cond ? $t : $f)"
  def lambdaExpr(params: String, body: String): String        = s"($params) -> $body"
  val nullLiteral: String                                     = "null"
  val thisLiteral: String                                     = "this"
  val superLiteral: String                                    = "super"

  def returnStmt(expr: String): String = s"return $expr;"
  def returnVoid: String               = "return;"

  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String = {
    val fin     = if (!mutable) "final " else ""
    val initStr = init.map(v => s" = $v").getOrElse("")
    s"$fin$typeName $name$initStr;"
  }
  def assignStmt(target: String, value: String): String = s"$target = $value;"
  def throwStmt(expr: String): String                   = s"throw $expr;"

  def forEachHeader(varName: String, typeName: String, iterExpr: String): String =
    s"for ($typeName $varName : $iterExpr)"

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

  def unaryOp(op: UnaryOp): String = op match {
    case UnaryOp.Not    => "!"
    case UnaryOp.Negate => "-"
    case UnaryOp.BitNot => "~"
  }

  // Java doesn't have native pattern matching (pre-21), rendered as if-chain
  val supportsNativeMatch: Boolean                            = false
  def matchHeader(expr: String): String                       = "" // unused for Java
  def matchCaseHeader(pattern: String): String                = ""
  def patternTypeTest(name: String, typeName: String): String = s"$name instanceof $typeName"
  def patternWildcard: String                                 = "default"
  def patternLiteral(value: String): String                   = value
}
