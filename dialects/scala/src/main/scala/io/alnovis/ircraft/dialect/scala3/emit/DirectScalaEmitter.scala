package io.alnovis.ircraft.dialect.scala3.emit

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.{ CommentStyle, Emitter, EmitterUtils }
import io.alnovis.ircraft.dialect.scala3.types.ScalaTypeMapping
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Emits Scala 3 source code from Semantic Dialect IR. */
class DirectScalaEmitter extends Emitter with EmitterUtils:

  private val tm = ScalaTypeMapping

  def emit(module: Module): Map[String, String] =
    module.topLevel
      .collect { case f: FileOp => f }
      .flatMap: f =>
        f.types.map: op =>
          val name    = typeOpName(op)
          val path    = f.packageName.replace('.', '/') + s"/$name.scala"
          val imports = collectImports(op)
          val source  = emitFile(f.packageName, imports, op)
          path -> source
      .toMap

  private def emitFile(pkg: String, imports: Set[String], op: Operation): String =
    val sb = StringBuilder()
    sb.append(s"package $pkg\n")
    if imports.nonEmpty then
      sb.append("\n")
      imports.toList.sorted.foreach(i => sb.append(s"import $i\n"))
    sb.append("\n")
    sb.append(emitTypeDecl(op, 0))
    sb.append("\n")
    sb.result()

  // ── Type declarations ──────────────────────────────────────────────────

  private def emitTypeDecl(op: Operation, level: Int): String = op match
    case i: InterfaceOp => emitTrait(i, level)
    case c: ClassOp     => emitClass(c, level)
    case e: EnumClassOp => emitEnum(e, level)
    case other          => s"// WARNING: unsupported operation type: ${other.qualifiedName}"

  private def emitTrait(i: InterfaceOp, level: Int): String =
    val sb = StringBuilder()
    i.javadoc.foreach(doc => sb.append(wrapComment(CommentStyle.ScalaDoc, doc, level) + "\n"))
    val extendsClause =
      if i.extendsTypes.isEmpty then ""
      else s" extends ${i.extendsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"
    sb.append(indent(level, s"trait ${i.name}$extendsClause:\n"))
    for m <- i.methods do sb.append(emitMethod(m, level + 1) + "\n")
    for n <- i.nestedTypes do sb.append(emitTypeDecl(n, level + 1) + "\n")
    if i.methods.isEmpty && i.nestedTypes.isEmpty then sb.append(indent(level + 1, "()\n"))
    sb.result()

  private def emitClass(c: ClassOp, level: Int): String =
    val sb = StringBuilder()
    c.javadoc.foreach(doc => sb.append(wrapComment(CommentStyle.ScalaDoc, doc, level) + "\n"))

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

    // Instance fields (non-static)
    val (staticFields, instanceFields) = c.fields.partition(f => f.modifiers.contains(Modifier.Static))
    for f <- instanceFields do sb.append(emitField(f, level + 1) + "\n")
    if instanceFields.nonEmpty && (c.constructors.nonEmpty || c.methods.nonEmpty) then sb.append("\n")

    // Constructors
    for ct <- c.constructors do sb.append(emitConstructor(ct, level + 1) + "\n")
    if c.constructors.nonEmpty && c.methods.nonEmpty then sb.append("\n")

    // Methods (non-static)
    val (staticMethods, instanceMethods) = c.methods.partition(_.modifiers.contains(Modifier.Static))
    for m <- instanceMethods do sb.append(emitMethod(m, level + 1) + "\n")

    // Nested types
    for n <- c.nestedTypes do sb.append(emitTypeDecl(n, level + 1) + "\n")

    // Companion object for static members
    if staticFields.nonEmpty || staticMethods.nonEmpty then
      sb.append("\n")
      sb.append(indent(level, s"object ${c.name}:\n"))
      for f <- staticFields do sb.append(emitField(f, level + 1) + "\n")
      for m <- staticMethods do sb.append(emitMethod(m, level + 1) + "\n")

    if c.fields.isEmpty && c.constructors.isEmpty && c.methods.isEmpty && c.nestedTypes.isEmpty then
      sb.append(indent(level + 1, "()\n"))

    sb.result()

  private def emitEnum(e: EnumClassOp, level: Int): String =
    val sb = StringBuilder()
    e.javadoc.foreach(doc => sb.append(wrapComment(CommentStyle.ScalaDoc, doc, level) + "\n"))
    val extendsClause =
      if e.implementsTypes.isEmpty then ""
      else s" extends ${e.implementsTypes.map(t => tm.toLanguageType(t)).mkString(" with ")}"

    // Enum with constructor parameters
    val hasConstructorParams = e.fields.nonEmpty
    val ctorParams =
      if hasConstructorParams then
        s"(${e.fields.map(f => s"val ${f.name}: ${tm.toLanguageType(f.fieldType)}").mkString(", ")})"
      else ""

    sb.append(indent(level, s"enum ${e.name}$ctorParams$extendsClause:\n"))

    // Constants
    for c <- e.constants do
      val args =
        if c.arguments.isEmpty then ""
        else s"(${c.arguments.map(emitExpr).mkString(", ")})"
      val ext = if hasConstructorParams then s" extends ${e.name}$args" else ""
      sb.append(indent(level + 1, s"case ${c.name}$ext\n"))

    // Methods
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
    m.javadoc.foreach(doc => sb.append(wrapComment(CommentStyle.ScalaDoc, doc, level) + "\n"))

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

  private def emitBlock(b: Block, level: Int): String =
    b.statements.map(s => emitStmt(s, level)).mkString("\n")

  private def emitStmt(s: Statement, level: Int): String = s match
    case Statement.ExpressionStmt(expr)    => indent(level, emitExpr(expr))
    case Statement.ReturnStmt(None)        => indent(level, "return")
    case Statement.ReturnStmt(Some(value)) => indent(level, emitExpr(value))
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

  // ── Expressions ────────────────────────────────────────────────────────

  private def emitExpr(e: Expression): String = e match
    case Expression.Literal(v, _) => v
    case Expression.Identifier(n) => n
    case Expression.MethodCall(recv, name, args, _) =>
      val r = recv.map(r => s"${emitExpr(r)}.").getOrElse("")
      val a = args.map(emitExpr).mkString(", ")
      s"$r$name($a)"
    case Expression.FieldAccess(recv, name) => s"${emitExpr(recv)}.$name"
    case Expression.NewInstance(t, args)    => s"${tm.toLanguageType(t)}(${args.map(emitExpr).mkString(", ")})"
    case Expression.Cast(expr, t)           => s"${emitExpr(expr)}.asInstanceOf[${tm.toLanguageType(t)}]"
    case Expression.Conditional(cond, t, f) => s"if ${emitExpr(cond)} then ${emitExpr(t)} else ${emitExpr(f)}"
    case Expression.BinaryOp(l, op, r)      => s"${emitExpr(l)} ${emitBinOp(op)} ${emitExpr(r)}"
    case Expression.UnaryOp(op, expr)       => s"${emitUnOp(op)}${emitExpr(expr)}"
    case Expression.Lambda(params, body) =>
      if params.isEmpty then s"() => ${emitExpr(body)}"
      else if params.size == 1 then s"${params.head} => ${emitExpr(body)}"
      else s"(${params.mkString(", ")}) => ${emitExpr(body)}"
    case Expression.NullLiteral => "null"
    case Expression.ThisRef     => "this"
    case Expression.SuperRef    => "super"

  private def emitBinOp(op: BinOperator): String = op match
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

  private def emitUnOp(op: UnOperator): String = op match
    case UnOperator.Not        => "!"
    case UnOperator.Negate     => "-"
    case UnOperator.BitwiseNot => "~"

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

  private def typeOpName(op: Operation): String = op match
    case i: InterfaceOp => i.name
    case c: ClassOp     => c.name
    case e: EnumClassOp => e.name
    case _              => "Unknown"

  private def collectImports(op: Operation): Set[String] =
    val imports = scala.collection.mutable.Set.empty[String]
    def walk(o: Operation): Unit = o match
      case c: ClassOp =>
        c.superClass.foreach(t => imports ++= tm.importsFor(t))
        c.implementsTypes.foreach(t => imports ++= tm.importsFor(t))
        c.fields.foreach(f => imports ++= tm.importsFor(f.fieldType))
        c.methods.foreach(m => imports ++= tm.importsFor(m.returnType))
        c.nestedTypes.foreach(walk)
      case i: InterfaceOp =>
        i.extendsTypes.foreach(t => imports ++= tm.importsFor(t))
        i.methods.foreach(m => imports ++= tm.importsFor(m.returnType))
        i.nestedTypes.foreach(walk)
      case m: MethodOp =>
        imports ++= tm.importsFor(m.returnType)
        m.parameters.foreach(p => imports ++= tm.importsFor(p.paramType))
      case e: EnumClassOp =>
        e.implementsTypes.foreach(t => imports ++= tm.importsFor(t))
        e.methods.foreach(m => imports ++= tm.importsFor(m.returnType))
      case _ => ()
    walk(op)
    imports.toSet
