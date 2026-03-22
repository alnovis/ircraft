package io.alnovis.ircraft.dialect.java.emit

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.{CommentStyle, Emitter, EmitterUtils}
import io.alnovis.ircraft.dialect.java.types.JavaTypeMapping
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Direct Java source emitter — no external dependencies.
  *
  * Transforms Semantic Dialect IR (FileOp, ClassOp, InterfaceOp, etc.) into Java source code strings.
  */
class DirectJavaEmitter extends Emitter with EmitterUtils:

  def emit(module: Module): Map[String, String] =
    module.topLevel.collect { case f: FileOp => emitFile(f) }.flatten.toMap

  private def emitFile(file: FileOp): Seq[(String, String)] =
    file.types.map: op =>
      val typeName = typeOpName(op)
      val path = file.packageName.replace('.', '/') + s"/$typeName.java"
      val imports = collectImports(op)
      val source = buildSource(file.packageName, imports, op)
      path -> source

  private def buildSource(pkg: String, imports: Set[String], op: Operation): String =
    val sb = StringBuilder()
    sb.append(s"package $pkg;\n\n")
    val sortedImports = imports.toList.sorted
    if sortedImports.nonEmpty then
      sortedImports.foreach(i => sb.append(s"import $i;\n"))
      sb.append("\n")
    sb.append(emitTypeDecl(op, 0))
    sb.append("\n")
    sb.toString

  // ── Type declarations ──────────────────────────────────────────────────

  private def emitTypeDecl(op: Operation, level: Int): String = op match
    case i: InterfaceOp  => emitInterface(i, level)
    case c: ClassOp      => emitClass(c, level)
    case e: EnumClassOp  => emitEnum(e, level)
    case _               => s"// unsupported: ${op.qualifiedName}"

  private def emitInterface(i: InterfaceOp, level: Int): String =
    val doc = i.javadoc.map(d => wrapComment(CommentStyle.JavaDoc, d, level) + "\n").getOrElse("")
    val annots = emitAnnotations(i.annotations, level)
    val mods = emitModifiers(i.modifiers - Modifier.Abstract)
    val ext = if i.extendsTypes.nonEmpty then s" extends ${i.extendsTypes.map(t => JavaTypeMapping.toJavaType(t)).mkString(", ")}" else ""
    val typeParams = emitTypeParams(i.typeParams)

    val members = Vector.newBuilder[String]
    i.methods.foreach(m => members += emitMethod(m, level + 1))
    i.nestedTypes.foreach(t => members += emitTypeDecl(t, level + 1))

    val body = joinLines(members.result(), "\n\n")
    s"$doc$annots${block(s"${mods}interface ${i.name}$typeParams$ext", level)(body)}"

  private def emitClass(c: ClassOp, level: Int): String =
    val doc = c.javadoc.map(d => wrapComment(CommentStyle.JavaDoc, d, level) + "\n").getOrElse("")
    val annots = emitAnnotations(c.annotations, level)
    val mods = emitModifiers(c.modifiers)
    val typeParams = emitTypeParams(c.typeParams)
    val ext = c.superClass.map(s => s" extends ${JavaTypeMapping.toJavaType(s)}").getOrElse("")
    val impl = if c.implementsTypes.nonEmpty then s" implements ${c.implementsTypes.map(t => JavaTypeMapping.toJavaType(t)).mkString(", ")}" else ""

    val members = Vector.newBuilder[String]
    c.fields.foreach(f => members += emitField(f, level + 1))
    if c.fields.nonEmpty && (c.constructors.nonEmpty || c.methods.nonEmpty) then members += ""
    c.constructors.foreach(ct => members += emitConstructor(c.name, ct, level + 1))
    c.methods.foreach(m => members += emitMethod(m, level + 1))
    c.nestedTypes.foreach(t => members += emitTypeDecl(t, level + 1))

    val body = joinLines(members.result(), "\n\n")
    s"$doc$annots${block(s"${mods}class ${c.name}$typeParams$ext$impl", level)(body)}"

  private def emitEnum(e: EnumClassOp, level: Int): String =
    val doc = e.javadoc.map(d => wrapComment(CommentStyle.JavaDoc, d, level) + "\n").getOrElse("")
    val annots = emitAnnotations(e.annotations, level)
    val mods = emitModifiers(e.modifiers)
    val impl = if e.implementsTypes.nonEmpty then s" implements ${e.implementsTypes.map(t => JavaTypeMapping.toJavaType(t)).mkString(", ")}" else ""

    val members = Vector.newBuilder[String]

    // Constants
    if e.constants.nonEmpty then
      val constantLines = e.constants.map: c =>
        val args = if c.arguments.nonEmpty then s"(${c.arguments.map(emitExpr).mkString(", ")})" else ""
        s"${c.name}$args"
      members += constantLines.map(indent(level + 1, _)).mkString(",\n") + ";"

    if e.fields.nonEmpty || e.constructors.nonEmpty || e.methods.nonEmpty then members += ""
    e.fields.foreach(f => members += emitField(f, level + 1))
    if e.fields.nonEmpty && (e.constructors.nonEmpty || e.methods.nonEmpty) then members += ""
    e.constructors.foreach(ct => members += emitConstructor(e.name, ct, level + 1))
    e.methods.foreach(m => members += emitMethod(m, level + 1))

    val body = joinLines(members.result(), "\n\n")
    s"$doc$annots${block(s"${mods}enum ${e.name}$impl", level)(body)}"

  // ── Members ────────────────────────────────────────────────────────────

  private def emitField(f: FieldDeclOp, level: Int): String =
    val doc = f.javadoc.map(d => wrapComment(CommentStyle.JavaDoc, d, level) + "\n").getOrElse("")
    val annots = emitAnnotations(f.annotations, level)
    val mods = emitModifiers(f.modifiers)
    val javaType = JavaTypeMapping.toJavaType(f.fieldType)
    val init = f.defaultValue.map(v => s" = $v").getOrElse("")
    s"$doc$annots${indent(level, s"${mods}$javaType ${f.name}$init;")}"

  private def emitConstructor(className: String, ct: ConstructorOp, level: Int): String =
    val mods = emitModifiers(ct.modifiers)
    val params = ct.parameters.map(p => s"${JavaTypeMapping.toJavaType(p.paramType)} ${p.name}").mkString(", ")
    val bodyStr = ct.body.map(b => emitBlock(b, level + 1)).getOrElse("")
    block(s"$mods$className($params)", level)(bodyStr)

  private def emitMethod(m: MethodOp, level: Int): String =
    val doc = m.javadoc.map(d => wrapComment(CommentStyle.JavaDoc, d, level) + "\n").getOrElse("")
    val annots = emitAnnotations(m.annotations, level)
    val modsWithoutAbstractDefault = emitModifiers(m.modifiers - Modifier.Abstract - Modifier.Default)
    val typeParams = emitTypeParams(m.typeParams)
    val returnType = JavaTypeMapping.toJavaType(m.returnType)
    val params = m.parameters.map(p => s"${JavaTypeMapping.toJavaType(p.paramType)} ${p.name}").mkString(", ")

    if m.isAbstract then
      // Keep abstract keyword for class methods, omit for interface methods
      val mods = emitModifiers(m.modifiers - Modifier.Default)
      s"$doc$annots${indent(level, s"${mods}$returnType$typeParams ${m.name}($params);")}"
    else if m.isDefault then
      val bodyStr = m.body.map(b => emitBlock(b, level + 1)).getOrElse("")
      s"$doc$annots${block(s"default $returnType$typeParams ${m.name}($params)", level)(bodyStr)}"
    else
      val bodyStr = m.body.map(b => emitBlock(b, level + 1)).getOrElse("")
      s"$doc$annots${block(s"$modsWithoutAbstractDefault$returnType$typeParams ${m.name}($params)", level)(bodyStr)}"

  // ── Expressions & Statements ───────────────────────────────────────────

  private def emitBlock(b: Block, level: Int): String =
    b.statements.map(s => emitStmt(s, level)).mkString("\n")

  private def emitStmt(s: Statement, level: Int): String = s match
    case Statement.ReturnStmt(Some(expr)) => indent(level, s"return ${emitExpr(expr)};")
    case Statement.ReturnStmt(None)       => indent(level, "return;")
    case Statement.ExpressionStmt(expr)   => indent(level, s"${emitExpr(expr)};")
    case Statement.VarDecl(name, varType, init, isFinal) =>
      val fin = if isFinal then "final " else ""
      val initStr = init.map(e => s" = ${emitExpr(e)}").getOrElse("")
      indent(level, s"$fin${JavaTypeMapping.toJavaType(varType)} $name$initStr;")
    case Statement.Assignment(target, value) =>
      indent(level, s"${emitExpr(target)} = ${emitExpr(value)};")
    case Statement.IfStmt(cond, thenBlock, elseBlock) =>
      val thenStr = block(s"if (${emitExpr(cond)})", level)(emitBlock(thenBlock, level + 1))
      elseBlock match
        case Some(eb) => s"$thenStr ${block("else", level)(emitBlock(eb, level + 1))}"
        case None     => thenStr
    case Statement.ForEachStmt(variable, varType, iterable, body) =>
      block(s"for (${JavaTypeMapping.toJavaType(varType)} $variable : ${emitExpr(iterable)})", level)(emitBlock(body, level + 1))
    case Statement.ThrowStmt(expr) =>
      indent(level, s"throw ${emitExpr(expr)};")
    case Statement.TryCatchStmt(tryBlock, catches, finallyBlock) =>
      val tryStr = block("try", level)(emitBlock(tryBlock, level + 1))
      val catchStr = catches.map: c =>
        block(s"catch (${JavaTypeMapping.toJavaType(c.exceptionType)} ${c.variableName})", level)(emitBlock(c.body, level + 1))
      val finallyStr = finallyBlock.map(b => s" ${block("finally", level)(emitBlock(b, level + 1))}").getOrElse("")
      s"$tryStr ${catchStr.mkString(" ")}$finallyStr"

  private def emitExpr(e: Expression): String = e match
    case Expression.Literal(value, _)            => value
    case Expression.Identifier(name)             => name
    case Expression.MethodCall(recv, name, args, _) =>
      val prefix = recv.map(r => s"${emitExpr(r)}.").getOrElse("")
      s"$prefix$name(${args.map(emitExpr).mkString(", ")})"
    case Expression.FieldAccess(recv, name)      => s"${emitExpr(recv)}.$name"
    case Expression.NewInstance(classType, args)  => s"new ${JavaTypeMapping.toJavaType(classType)}(${args.map(emitExpr).mkString(", ")})"
    case Expression.Cast(expr, targetType)        => s"(${JavaTypeMapping.toJavaType(targetType)}) ${emitExpr(expr)}"
    case Expression.Conditional(cond, t, f)       => s"(${emitExpr(cond)} ? ${emitExpr(t)} : ${emitExpr(f)})"
    case Expression.BinaryOp(l, op, r)            => s"(${emitExpr(l)} ${binOpStr(op)} ${emitExpr(r)})"
    case Expression.UnaryOp(op, expr)             => s"${unOpStr(op)}${emitExpr(expr)}"
    case Expression.Lambda(params, body)          => s"(${params.mkString(", ")}) -> ${emitExpr(body)}"
    case Expression.NullLiteral                   => "null"
    case Expression.ThisRef                       => "this"
    case Expression.SuperRef                      => "super"

  private def binOpStr(op: BinOperator): String = op match
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

  private def unOpStr(op: UnOperator): String = op match
    case UnOperator.Not        => "!"
    case UnOperator.Negate     => "-"
    case UnOperator.BitwiseNot => "~"

  // ── Helpers ────────────────────────────────────────────────────────────

  private def emitModifiers(mods: Set[Modifier]): String =
    val order = List(
      Modifier.Public, Modifier.Protected, Modifier.Private,
      Modifier.Abstract, Modifier.Static, Modifier.Final,
      Modifier.Synchronized, Modifier.Volatile, Modifier.Transient, Modifier.Native,
    )
    val result = order.filter(mods.contains).map(modStr).mkString(" ")
    if result.isEmpty then "" else result + " "

  private def modStr(m: Modifier): String = m match
    case Modifier.Public        => "public"
    case Modifier.Protected     => "protected"
    case Modifier.Private       => "private"
    case Modifier.PackagePrivate => ""
    case Modifier.Abstract      => "abstract"
    case Modifier.Final         => "final"
    case Modifier.Static        => "static"
    case Modifier.Sealed        => "sealed"
    case Modifier.Override      => ""  // handled as @Override annotation
    case Modifier.Default       => ""  // handled separately
    case Modifier.Synchronized  => "synchronized"
    case Modifier.Volatile      => "volatile"
    case Modifier.Transient     => "transient"
    case Modifier.Native        => "native"

  private def emitTypeParams(tps: List[TypeParam]): String =
    if tps.isEmpty then ""
    else
      val params = tps.map: tp =>
        if tp.upperBounds.nonEmpty then s"${tp.name} extends ${tp.upperBounds.map(JavaTypeMapping.toJavaType).mkString(" & ")}"
        else tp.name
      s"<${params.mkString(", ")}>"

  private def emitAnnotations(annots: List[String], level: Int): String =
    if annots.isEmpty then ""
    else annots.map(a => indent(level, s"@$a")).mkString("\n") + "\n"

  private def typeOpName(op: Operation): String = op match
    case i: InterfaceOp => i.name
    case c: ClassOp     => c.name
    case e: EnumClassOp => e.name
    case _              => "Unknown"

  private def collectImports(op: Operation): Set[String] =
    val imports = Set.newBuilder[String]
    def walk(o: Operation): Unit = o match
      case c: ClassOp =>
        c.superClass.foreach(t => imports ++= JavaTypeMapping.importsFor(t))
        c.implementsTypes.foreach(t => imports ++= JavaTypeMapping.importsFor(t))
        c.fields.foreach(f => imports ++= JavaTypeMapping.importsFor(f.fieldType))
        c.methods.foreach(walk)
        c.nestedTypes.foreach(walk)
      case i: InterfaceOp =>
        i.extendsTypes.foreach(t => imports ++= JavaTypeMapping.importsFor(t))
        i.methods.foreach(walk)
        i.nestedTypes.foreach(walk)
      case m: MethodOp =>
        imports ++= JavaTypeMapping.importsFor(m.returnType)
        m.parameters.foreach(p => imports ++= JavaTypeMapping.importsFor(p.paramType))
      case e: EnumClassOp =>
        e.methods.foreach(walk)
      case _ => ()
    walk(op)
    imports.result()
