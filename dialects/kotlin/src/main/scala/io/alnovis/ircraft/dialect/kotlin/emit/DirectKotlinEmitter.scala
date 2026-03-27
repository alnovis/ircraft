package io.alnovis.ircraft.dialect.kotlin.emit

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.CommentStyle
import io.alnovis.ircraft.dialect.kotlin.types.KotlinTypeMapping
import io.alnovis.ircraft.dialect.semantic.emit.BaseEmitter
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Emits Kotlin source code from Semantic Dialect IR. */
class DirectKotlinEmitter extends BaseEmitter:

  protected val tm: LanguageTypeMapping    = KotlinTypeMapping
  protected val fileExtension: String      = "kt"
  protected val commentStyle: CommentStyle = CommentStyle.KDoc
  protected val statementTerminator: String = ""

  // ── Expression hooks ──────────────────────────────────────────────────

  protected def emitCast(expr: Expression, t: TypeRef): String =
    s"${emitExpr(expr)} as ${tm.toLanguageType(t)}"

  protected def emitNewInstance(t: TypeRef, args: List[Expression]): String =
    s"${tm.toLanguageType(t)}(${args.map(emitExpr).mkString(", ")})"

  protected def emitConditional(cond: Expression, t: Expression, f: Expression): String =
    s"if (${emitExpr(cond)}) ${emitExpr(t)} else ${emitExpr(f)}"

  protected def emitUnaryOp(op: UnOperator, expr: Expression): String =
    if op == UnOperator.BitwiseNot then s"${emitExpr(expr)}.inv()"
    else
      val opStr = op match
        case UnOperator.Not    => "!"
        case UnOperator.Negate => "-"
        case _                 => "~"
      s"$opStr${emitExpr(expr)}"

  protected def emitLambda(params: List[String], body: Expression): String =
    if params.isEmpty then s"{ ${emitExpr(body)} }"
    else s"{ ${params.mkString(", ")} -> ${emitExpr(body)} }"

  override protected def emitBinOp(op: BinOperator): String = op match
    case BinOperator.BitAnd => "and"
    case BinOperator.BitOr  => "or"
    case BinOperator.BitXor => "xor"
    case other              => super.emitBinOp(other)

  // ── Type declarations ──────────────────────────────────────────────────

  protected def emitTypeDecl(op: Operation, level: Int): String = op match
    case i: InterfaceOp => emitInterface(i, level)
    case c: ClassOp     => emitClass(c, level)
    case e: EnumClassOp => emitEnum(e, level)
    case other          => s"// WARNING: unsupported operation type: ${other.qualifiedName}"

  private def emitInterface(i: InterfaceOp, level: Int): String =
    val sb = StringBuilder()
    i.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))
    emitAnnotations(i.annotations, level, sb)
    val extendsClause =
      if i.extendsTypes.isEmpty then ""
      else s" : ${i.extendsTypes.map(t => tm.toLanguageType(t)).mkString(", ")}"
    sb.append(indent(level, s"interface ${i.name}$extendsClause {\n"))
    for m <- i.methods do sb.append(emitMethod(m, level + 1) + "\n")
    for n <- i.nestedTypes do sb.append(emitTypeDecl(n, level + 1) + "\n")
    sb.append(indent(level, "}"))
    sb.result()

  private def emitClass(c: ClassOp, level: Int): String =
    val sb = StringBuilder()
    c.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))
    emitAnnotations(c.annotations, level, sb)

    val mods        = emitModifiers(c.modifiers)
    val kw          = if c.isAbstract then "abstract class" else "class"
    val tparams     = emitTypeParams(c.typeParams)
    val superClause = c.superClass.map(s => s" : ${tm.toLanguageType(s)}").getOrElse("")
    val implClause =
      if c.implementsTypes.isEmpty then ""
      else if c.superClass.isDefined then s", ${c.implementsTypes.map(t => tm.toLanguageType(t)).mkString(", ")}"
      else s" : ${c.implementsTypes.map(t => tm.toLanguageType(t)).mkString(", ")}"

    sb.append(indent(level, s"$mods$kw ${c.name}$tparams$superClause$implClause {\n"))

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
      sb.append(indent(level + 1, "companion object {\n"))
      for f <- staticFields do sb.append(emitField(f, level + 2) + "\n")
      for m <- staticMethods do sb.append(emitMethod(m, level + 2) + "\n")
      sb.append(indent(level + 1, "}\n"))

    sb.append(indent(level, "}"))
    sb.result()

  private def emitEnum(e: EnumClassOp, level: Int): String =
    val sb = StringBuilder()
    e.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))
    emitAnnotations(e.annotations, level, sb)
    val implClause =
      if e.implementsTypes.isEmpty then ""
      else s" : ${e.implementsTypes.map(t => tm.toLanguageType(t)).mkString(", ")}"
    sb.append(indent(level, s"enum class ${e.name}$implClause {\n"))

    val constLines = e.constants.map: c =>
      val args = if c.arguments.isEmpty then "" else s"(${c.arguments.map(emitExpr).mkString(", ")})"
      s"${c.name}$args"
    if constLines.nonEmpty then
      sb.append(indent(level + 1, constLines.mkString(",\n" + "  " * (level + 1))))
      sb.append(";\n")

    if e.fields.nonEmpty || e.constructors.nonEmpty || e.methods.nonEmpty then sb.append("\n")
    for f <- e.fields do sb.append(emitField(f, level + 1) + "\n")
    for ct <- e.constructors do sb.append(emitConstructor(ct, level + 1) + "\n")
    for m <- e.methods do sb.append(emitMethod(m, level + 1) + "\n")

    sb.append(indent(level, "}"))
    sb.result()

  // ── Members ────────────────────────────────────────────────────────────

  private def emitField(f: FieldDeclOp, level: Int): String =
    val mods       = emitFieldModifiers(f.modifiers)
    val mutability = if f.modifiers.contains(Modifier.Final) then "val" else "var"
    val init       = f.defaultValue.map(v => s" = $v").getOrElse("")
    indent(level, s"$mods$mutability ${f.name}: ${tm.toLanguageType(f.fieldType)}$init")

  private def emitConstructor(ct: ConstructorOp, level: Int): String =
    val mods    = emitModifiers(ct.modifiers - Modifier.Public)
    val params  = ct.parameters.map(p => s"${p.name}: ${tm.toLanguageType(p.paramType)}").mkString(", ")
    val bodyStr = ct.body.map(b => emitBlock(b, level + 1)).getOrElse("")
    block(s"${mods}constructor($params)", level)(bodyStr)

  private def emitMethod(m: MethodOp, level: Int): String =
    val sb = StringBuilder()
    m.javadoc.foreach(doc => sb.append(wrapComment(commentStyle, doc, level) + "\n"))

    val mods    = emitMethodModifiers(m.modifiers)
    val tparams = emitTypeParams(m.typeParams)
    val params  = m.parameters.map(p => s"${p.name}: ${tm.toLanguageType(p.paramType)}").mkString(", ")
    val retType = if m.returnType == TypeRef.VOID then "" else s": ${tm.toLanguageType(m.returnType)}"

    if m.isAbstract then sb.append(indent(level, s"${mods}fun $tparams${m.name}($params)$retType"))
    else
      val bodyStr = m.body.map(b => emitBlock(b, level + 1)).getOrElse("")
      sb.append(block(s"${mods}fun $tparams${m.name}($params)$retType", level)(bodyStr))
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
      val result  = block(s"if (${emitExpr(cond)})", level)(thenStr)
      elseBlock
        .map: eb =>
          val elseStr = emitBlock(eb, level + 1)
          result + " else {\n" + elseStr + "\n" + indent(level, "}")
        .getOrElse(result)
    case Statement.ForEachStmt(v, t, iterable, body) =>
      val bodyStr = emitBlock(body, level + 1)
      block(s"for ($v: ${tm.toLanguageType(t)} in ${emitExpr(iterable)})", level)(bodyStr)
    case Statement.ThrowStmt(expr) => indent(level, s"throw ${emitExpr(expr)}")
    case Statement.TryCatchStmt(tryBlock, catches, fin) =>
      val sb = StringBuilder()
      sb.append(block("try", level)(emitBlock(tryBlock, level + 1)))
      for c <- catches do
        val catchBody = emitBlock(c.body, level + 1)
        sb.append(
          s" catch (${c.variableName}: ${tm.toLanguageType(c.exceptionType)}) {\n$catchBody\n${indent(level, "}")}"
        )
      fin.foreach(f => sb.append(s" finally {\n${emitBlock(f, level + 1)}\n${indent(level, "}")}"))
      sb.result()

  // ── Modifiers ──────────────────────────────────────────────────────────

  private def emitModifiers(mods: Set[Modifier]): String =
    val parts = List.newBuilder[String]
    if mods.contains(Modifier.Private) then parts += "private"
    if mods.contains(Modifier.Protected) then parts += "protected"
    if mods.contains(Modifier.Abstract) then parts += "abstract"
    if mods.contains(Modifier.Override) then parts += "override"
    if mods.contains(Modifier.Sealed) then parts += "sealed"
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
    if mods.contains(Modifier.Abstract) then parts += "abstract"
    if mods.contains(Modifier.Override) then parts += "override"
    if mods.contains(Modifier.Sealed) then parts += "sealed"
    val result = parts.result()
    if result.isEmpty then "" else result.mkString(" ") + " "

  // ── Helpers ────────────────────────────────────────────────────────────

  private def emitTypeParams(tps: List[TypeParam]): String =
    if tps.isEmpty then ""
    else
      val params = tps.map: tp =>
        if tp.upperBounds.isEmpty then tp.name
        else s"${tp.name} : ${tp.upperBounds.map(tm.toLanguageType).mkString(", ")}"
      s"<${params.mkString(", ")}> "

  private def emitAnnotations(anns: List[String], level: Int, sb: StringBuilder): Unit =
    for a <- anns do
      if a != "Override" then
        sb.append(indent(level, s"@$a\n"))
