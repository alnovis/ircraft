package io.alnovis.ircraft.dialect.scala3.emit

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.CommentStyle
import io.alnovis.ircraft.dialect.scala3.types.ScalaTypeMapping
import io.alnovis.ircraft.dialect.semantic.emit.BaseEmitter
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Emits Scala 3 source code from Semantic Dialect IR. */
class DirectScalaEmitter extends BaseEmitter:

  protected val tm: LanguageTypeMapping    = ScalaTypeMapping
  protected val fileExtension: String      = "scala"
  protected val commentStyle: CommentStyle = CommentStyle.ScalaDoc
  protected val statementTerminator: String = ""

  // ── Expression hooks ──────────────────────────────────────────────────

  protected def emitCast(expr: Expression, t: TypeRef): String =
    s"${emitExpr(expr)}.asInstanceOf[${tm.toLanguageType(t)}]"

  protected def emitNewInstance(t: TypeRef, args: List[Expression]): String =
    s"${tm.toLanguageType(t)}(${args.map(emitExpr).mkString(", ")})"

  protected def emitConditional(cond: Expression, t: Expression, f: Expression): String =
    s"if ${emitExpr(cond)} then ${emitExpr(t)} else ${emitExpr(f)}"

  protected def emitUnaryOp(op: UnOperator, expr: Expression): String =
    val opStr = op match
      case UnOperator.Not        => "!"
      case UnOperator.Negate     => "-"
      case UnOperator.BitwiseNot => "~"
    s"$opStr${emitExpr(expr)}"

  protected def emitLambda(params: List[String], body: Expression): String =
    if params.isEmpty then s"() => ${emitExpr(body)}"
    else if params.size == 1 then s"${params.head} => ${emitExpr(body)}"
    else s"(${params.mkString(", ")}) => ${emitExpr(body)}"

  // ── Type declarations ──────────────────────────────────────────────────

  protected def emitTypeDecl(op: Operation, level: Int): String = op match
    case i: InterfaceOp => emitTrait(i, level)
    case c: ClassOp     => emitClass(c, level)
    case e: EnumClassOp => emitEnum(e, level)
    case other          => s"// WARNING: unsupported operation type: ${other.qualifiedName}"

  private def emitTrait(i: InterfaceOp, level: Int): String =
    val sb = StringBuilder()
    i.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))
    val extendsClause =
      if i.extendsTypes.isEmpty then ""
      else s" extends ${i.extendsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"
    sb.append(indent(level, s"trait ${i.name}$extendsClause:\n"))
    for m <- i.methods do sb.append(emitMethod(m, level + 1) + "\n")
    for n <- i.nestedTypes do sb.append(emitTypeDecl(n, level + 1) + "\n")
    sb.result()

  private def emitClass(c: ClassOp, level: Int): String =
    val sb = StringBuilder()
    c.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))

    val mods        = emitModifiers(c.modifiers)
    val kw          = if c.isAbstract then "abstract class" else "class"
    val tparams     = emitTypeParams(c.typeParams)
    val superClause = c.superClass.map(s => s" extends ${tm.toLanguageType(s)}").getOrElse("")
    val withClause =
      if c.implementsTypes.isEmpty then ""
      else if c.superClass.isDefined then
        s" with ${c.implementsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"
      else s" extends ${c.implementsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"

    sb.append(indent(level, s"$mods$kw ${c.name}$tparams$superClause$withClause:\n"))

    val (staticFields, instanceFields) = c.fields.partition(f => f.modifiers.contains(Modifier.Static))
    for f <- instanceFields do sb.append(emitField(f, level + 1) + "\n")
    if instanceFields.nonEmpty && (c.constructors.nonEmpty || c.methods.nonEmpty) then sb.append("\n")

    for ct <- c.constructors do sb.append(emitConstructor(ct, level + 1) + "\n")
    if c.constructors.nonEmpty && c.methods.nonEmpty then sb.append("\n")

    val (staticMethods, instanceMethods) = c.methods.partition(_.modifiers.contains(Modifier.Static))
    for m <- instanceMethods do sb.append(emitMethod(m, level + 1) + "\n")

    for n <- c.nestedTypes do sb.append(emitTypeDecl(n, level + 1) + "\n")

    if staticFields.nonEmpty || staticMethods.nonEmpty then
      sb.append("\n")
      sb.append(indent(level, s"object ${c.name}:\n"))
      for f <- staticFields do sb.append(emitField(f, level + 1) + "\n")
      for m <- staticMethods do sb.append(emitMethod(m, level + 1) + "\n")

    sb.result()

  private def emitEnum(e: EnumClassOp, level: Int): String =
    val sb = StringBuilder()
    e.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))
    val extendsClause =
      if e.implementsTypes.isEmpty then ""
      else s" extends ${e.implementsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"

    val hasConstructorParams = e.fields.nonEmpty
    val ctorParams =
      if hasConstructorParams then
        s"(${e.fields.map(f => s"val ${f.name}: ${tm.toLanguageType(f.fieldType)}").mkString(", ")})"
      else ""

    sb.append(indent(level, s"enum ${e.name}$ctorParams$extendsClause:\n"))

    for c <- e.constants do
      val args =
        if c.arguments.isEmpty then ""
        else s"(${c.arguments.map(emitExpr).mkString(", ")})"
      val ext = if hasConstructorParams then s" extends ${e.name}$args" else ""
      sb.append(indent(level + 1, s"case ${c.name}$ext\n"))

    if e.methods.nonEmpty then sb.append("\n")
    for m <- e.methods do sb.append(emitMethod(m, level + 1) + "\n")

    sb.result()

  // ── Members ────────────────────────────────────────────────────────────

  private def emitField(f: FieldDeclOp, level: Int): String =
    val mods       = emitFieldModifiers(f.modifiers)
    val mutability = if f.modifiers.contains(Modifier.Final) then "val" else "var"
    val init       = f.defaultValue.map(v => s" = $v").getOrElse("")
    indent(level, s"$mods$mutability ${f.name}: ${tm.toLanguageType(f.fieldType)}$init")

  private def emitConstructor(ct: ConstructorOp, level: Int): String =
    val params  = ct.parameters.map(p => s"${p.name}: ${tm.toLanguageType(p.paramType)}").mkString(", ")
    val bodyStr = ct.body.map(b => "\n" + emitBlock(b, level + 1)).getOrElse("")
    indent(level, s"def this($params) =$bodyStr")

  private def emitMethod(m: MethodOp, level: Int): String =
    val sb = StringBuilder()
    m.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))

    val mods    = emitMethodModifiers(m.modifiers)
    val tparams = emitTypeParams(m.typeParams)
    val params =
      if m.parameters.isEmpty then ""
      else s"(${m.parameters.map(p => s"${p.name}: ${tm.toLanguageType(p.paramType)}").mkString(", ")})"
    val retType = if m.returnType == TypeRef.VOID then "" else s": ${tm.toLanguageType(m.returnType)}"

    if m.isAbstract then sb.append(indent(level, s"${mods}def ${m.name}$tparams$params$retType"))
    else
      val bodyStr = m.body.map(b => emitBlock(b, level + 1)).getOrElse("???")
      sb.append(indent(level, s"${mods}def ${m.name}$tparams$params$retType =\n$bodyStr"))
    sb.result()

  // ── Statements ─────────────────────────────────────────────────────────

  protected def emitStmt(s: Statement, level: Int): String = s match
    case Statement.ExpressionStmt(expr)    => indent(level, emitExpr(expr))
    case Statement.ReturnStmt(None)        => indent(level, "return")
    case Statement.ReturnStmt(Some(value)) => indent(level, s"return ${emitExpr(value)}")
    case Statement.VarDecl(n, t, init, fin) =>
      val kw      = if fin then "val" else "var"
      val initStr = init.map(e => s" = ${emitExpr(e)}").getOrElse("")
      indent(level, s"$kw $n: ${tm.toLanguageType(t)}$initStr")
    case Statement.Assignment(target, value) => indent(level, s"${emitExpr(target)} = ${emitExpr(value)}")
    case Statement.IfStmt(cond, thenBlock, elseBlock) =>
      val thenStr = emitBlock(thenBlock, level + 1)
      val base    = indent(level, s"if ${emitExpr(cond)} then\n$thenStr")
      elseBlock
        .map: eb =>
          val elseStr = emitBlock(eb, level + 1)
          base + "\n" + indent(level, s"else\n$elseStr")
        .getOrElse(base)
    case Statement.ForEachStmt(v, t, iterable, body) =>
      val bodyStr = emitBlock(body, level + 1)
      indent(level, s"for $v: ${tm.toLanguageType(t)} <- ${emitExpr(iterable)} do\n$bodyStr")
    case Statement.ThrowStmt(expr) => indent(level, s"throw ${emitExpr(expr)}")
    case Statement.TryCatchStmt(tryBlock, catches, fin) =>
      val sb = StringBuilder()
      sb.append(indent(level, "try\n" + emitBlock(tryBlock, level + 1)))
      if catches.nonEmpty then
        sb.append("\n" + indent(level, "catch"))
        for c <- catches do
          sb.append(
            "\n" + indent(
              level + 1,
              s"case ${c.variableName}: ${tm.toLanguageType(c.exceptionType)} =>\n${emitBlock(c.body, level + 2)}"
            )
          )
      fin.foreach(f => sb.append("\n" + indent(level, s"finally\n${emitBlock(f, level + 1)}")))
      sb.result()

  // ── Modifiers ──────────────────────────────────────────────────────────

  private def emitModifiers(mods: Set[Modifier]): String =
    val parts = List.newBuilder[String]
    if mods.contains(Modifier.Private) then parts += "private"
    if mods.contains(Modifier.Protected) then parts += "protected"
    if mods.contains(Modifier.Sealed) then parts += "sealed"
    if mods.contains(Modifier.Abstract) then parts += "abstract"
    if mods.contains(Modifier.Final) then parts += "final"
    val result = parts.result()
    if result.isEmpty then "" else result.mkString(" ") + " "

  private def emitFieldModifiers(mods: Set[Modifier]): String =
    val parts = List.newBuilder[String]
    if mods.contains(Modifier.Private) then parts += "private"
    if mods.contains(Modifier.Protected) then parts += "protected"
    if mods.contains(Modifier.Override) then parts += "override"
    val result = parts.result()
    if result.isEmpty then "" else result.mkString(" ") + " "

  private def emitMethodModifiers(mods: Set[Modifier]): String =
    val parts = List.newBuilder[String]
    if mods.contains(Modifier.Private) then parts += "private"
    if mods.contains(Modifier.Protected) then parts += "protected"
    if mods.contains(Modifier.Override) then parts += "override"
    if mods.contains(Modifier.Final) then parts += "final"
    val result = parts.result()
    if result.isEmpty then "" else result.mkString(" ") + " "

  // ── Helpers ────────────────────────────────────────────────────────────

  private def emitTypeParams(tps: List[TypeParam]): String =
    if tps.isEmpty then ""
    else
      val params = tps.map: tp =>
        if tp.upperBounds.isEmpty then tp.name
        else s"${tp.name} <: ${tp.upperBounds.map(tm.toLanguageType).mkString(" with ")}"
      s"[${params.mkString(", ")}]"
