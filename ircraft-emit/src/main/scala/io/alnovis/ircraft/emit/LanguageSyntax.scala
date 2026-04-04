package io.alnovis.ircraft.emit

import io.alnovis.ircraft.core.ir._

/** Language-specific syntax decisions. Parameterizes BaseEmitter for any target language. */
trait LanguageSyntax {

  // -- File structure --
  def fileExtension: String
  def statementTerminator: String
  def packageDecl(pkg: String): String

  // -- Type declarations --
  def typeSignature(vis: String, kind: TypeKind, name: String, typeParams: String, supertypes: Vector[String]): String
  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String
  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String

  // -- Fields --
  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String
  def constDecl(vis: String, typeName: String, name: String, value: String): String

  // -- Functions --
  def funcSignature(
    vis: String,
    modifiers: Set[FuncModifier],
    typeParams: String,
    returnType: String,
    name: String,
    params: String,
    isAbstract: Boolean,
    parentKind: TypeKind
  ): String
  def paramDecl(name: String, typeName: String): String

  // -- Visibility --
  def visibility(v: Visibility): String

  // -- Type parameters --
  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String

  // -- Expressions --
  def newExpr(typeName: String, args: String): String
  def castExpr(expr: String, typeName: String): String
  def ternaryExpr(cond: String, t: String, f: String): String
  def lambdaExpr(params: String, body: String): String
  def nullLiteral: String
  def thisLiteral: String
  def superLiteral: String

  // -- Statements --
  def returnStmt(expr: String): String
  def returnVoid: String
  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String
  def assignStmt(target: String, value: String): String
  def throwStmt(expr: String): String
  def forEachHeader(varName: String, typeName: String, iterExpr: String): String

  // -- Documentation --
  /** Render structured Doc to language-specific comment format. Default: Javadoc/Scaladoc style. */
  def renderDoc(doc: Doc): String = {
    val lines = Vector.newBuilder[String]
    lines += doc.summary
    doc.description.foreach { d =>
      lines += ""
      lines += d
    }
    if (doc.params.nonEmpty) {
      lines += ""
      doc.params.foreach { case (name, desc) => lines += s"@param $name $desc" }
    }
    doc.returns.foreach(r => lines += s"@return $r")
    doc.throws.foreach { case (ex, desc) => lines += s"@throws $ex $desc" }
    doc.tags.foreach { case (tag, value) => lines += s"@$tag $value" }
    if (doc.examples.nonEmpty) {
      lines += ""
      doc.examples.foreach { ex =>
        lines += "{{{"
        lines += ex
        lines += "}}}"
      }
    }
    val content = lines.result()
    if (content.size == 1) s"/** ${content.head} */"
    else ("/**" +: content.map(l => s" * $l") :+ " */").mkString("\n")
  }

  // -- Naming conventions --
  def transformMethodName(name: String): String = name // override for Scala: strip "get", camelCase
  def transformFieldName(name: String): String  = name // override for Scala: camelCase

  // -- Body style --
  /** true = Scala style `def x: Int = expr`, false = Java style `int x() { return expr; }` */
  def useFuncEqualsStyle: Boolean = false

  // -- Pattern matching --
  def matchHeader(expr: String): String // "expr match" (Scala) or "// match on expr" (Java -- rendered as if-chain)
  def matchCaseHeader(pattern: String): String // "case pattern =>" (Scala) or "case pattern:" (Java switch)
  def patternTypeTest(name: String, typeName: String): String // "name: Type" (Scala) or "Type name" (Java instanceof)
  def patternWildcard: String                                 // "_" (Scala) or "default" (Java)
  def patternLiteral(value: String): String                   // value as-is
  def supportsNativeMatch: Boolean                            // true = render as match, false = render as if-chain

  // -- Operators --
  def binOp(op: BinaryOp): String
  def unaryOp(op: UnaryOp): String
}
