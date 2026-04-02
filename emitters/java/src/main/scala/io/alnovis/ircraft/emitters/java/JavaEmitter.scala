package io.alnovis.ircraft.emitters.java

import cats.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emit.{BaseEmitter, CodeNode, TypeMapping}

class JavaEmitter[F[_]: Monad] extends BaseEmitter[F]:

  protected val tm: TypeMapping = JavaTypeMapping
  protected val fileExtension: String = "java"
  protected val statementTerminator: String = ";"

  // -- Phase 1: Decl -> F[CodeNode] ------------------------------------------

  protected def emitDeclTree(decl: Decl): F[CodeNode] = decl match
    case td: Decl.TypeDecl  => emitTypeDecl(td)
    case ed: Decl.EnumDecl  => emitEnumDecl(ed)
    case fd: Decl.FuncDecl  => emitFuncNode(fd.func, TypeKind.Product)
    case cd: Decl.ConstDecl => Monad[F].pure(emitConstDecl(cd))
    case _: Decl.AliasDecl  => Monad[F].pure(CodeNode.Line(""))

  private def emitTypeDecl(td: Decl.TypeDecl): F[CodeNode] =
    for
      fieldNodes  <- td.fields.traverse(f => Monad[F].pure(emitField(f)))
      funcNodes   <- td.functions.traverse(f => emitFuncNode(f, td.kind))
      nestedNodes <- td.nested.traverse(emitDeclTree)
    yield
      val sig = typeSignature(td)
      val sections = Vector(fieldNodes, funcNodes, nestedNodes).filter(_.nonEmpty)
      val annots = td.annotations.map(a => CodeNode.Line(s"@${a.name}"))
      if annots.isEmpty then CodeNode.TypeBlock(sig, sections)
      else CodeNode.Block(annots :+ CodeNode.TypeBlock(sig, sections))

  private def emitEnumDecl(ed: Decl.EnumDecl): F[CodeNode] =
    for
      funcNodes <- ed.functions.traverse(f => emitFuncNode(f, TypeKind.Product))
    yield
      val vis = visStr(ed.visibility)
      val impl = if ed.supertypes.nonEmpty then s" implements ${ed.supertypes.map(tm.typeName).mkString(", ")}" else ""
      val sig = s"${vis}enum ${ed.name}$impl"

      val constantSection =
        if ed.variants.isEmpty then Vector.empty
        else
          val lastIdx = ed.variants.size - 1
          ed.variants.zipWithIndex.map { (v, idx) =>
            val args = if v.args.nonEmpty then s"(${v.args.map(emitExprText).mkString(", ")})" else ""
            val suffix = if idx == lastIdx then ";" else ","
            CodeNode.Line(s"${v.name}$args$suffix")
          }

      val sections = Vector(constantSection, funcNodes).filter(_.nonEmpty)
      val annots = ed.annotations.map(a => CodeNode.Line(s"@${a.name}"))
      if annots.isEmpty then CodeNode.TypeBlock(sig, sections)
      else CodeNode.Block(annots :+ CodeNode.TypeBlock(sig, sections))

  private def emitFuncNode(f: Func, parentKind: TypeKind): F[CodeNode] =
    val sig = funcSignature(f, parentKind)
    f.body match
      case None =>
        Monad[F].pure(CodeNode.Func(sig, None))
      case Some(body) =>
        body.stmts.traverse(emitStmtNode).map { stmtNodes =>
          CodeNode.Func(sig, Some(stmtNodes))
        }

  private def emitStmtNode(s: Stmt): F[CodeNode] = s match
    case Stmt.Return(Some(e)) =>
      Monad[F].pure(CodeNode.Line(s"return ${emitExprText(e)};"))
    case Stmt.Return(None) =>
      Monad[F].pure(CodeNode.Line("return;"))
    case Stmt.Eval(e) =>
      Monad[F].pure(CodeNode.Line(s"${emitExprText(e)};"))
    case Stmt.Let(n, t, init, mut) =>
      val fin = if !mut then "final " else ""
      val initStr = init.map(e => s" = ${emitExprText(e)}").getOrElse("")
      Monad[F].pure(CodeNode.Line(s"$fin${tm.typeName(t)} $n$initStr;"))
    case Stmt.Assign(target, value) =>
      Monad[F].pure(CodeNode.Line(s"${emitExprText(target)} = ${emitExprText(value)};"))
    case Stmt.If(cond, thenBody, elseBody) =>
      for
        thenNodes <- thenBody.stmts.traverse(emitStmtNode)
        elseNodes <- elseBody.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.IfElse(emitExprText(cond), thenNodes, elseNodes)
    case Stmt.ForEach(v, t, iter, body) =>
      body.stmts.traverse(emitStmtNode).map { bodyNodes =>
        CodeNode.ForLoop(s"for (${tm.typeName(t)} $v : ${emitExprText(iter)})", bodyNodes)
      }
    case Stmt.While(cond, body) =>
      body.stmts.traverse(emitStmtNode).map { bodyNodes =>
        CodeNode.WhileLoop(emitExprText(cond), bodyNodes)
      }
    case Stmt.Switch(expr, cases, default) =>
      for
        caseNodes <- cases.traverse { sc =>
          sc.body.stmts.traverse(emitStmtNode).map(ns => (emitExprText(sc.pattern), ns))
        }
        defaultNodes <- default.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.SwitchBlock(emitExprText(expr), caseNodes, defaultNodes)
    case Stmt.Throw(e) =>
      Monad[F].pure(CodeNode.Line(s"throw ${emitExprText(e)};"))
    case Stmt.Comment(text) =>
      Monad[F].pure(CodeNode.Comment(text))
    case Stmt.TryCatch(tryBody, catches, finallyBody) =>
      for
        tryNodes     <- tryBody.stmts.traverse(emitStmtNode)
        catchNodes   <- catches.traverse(c => c.body.stmts.traverse(emitStmtNode).map(ns => (s"${tm.typeName(c.exType)} ${c.name}", ns)))
        finallyNodes <- finallyBody.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.TryCatch(tryNodes, catchNodes, finallyNodes)

  // -- Expr -> String (pure, no effects needed here) ----------------------

  protected def emitExprText(expr: Expr): String = expr match
    case Expr.Lit(v, _)             => v
    case Expr.Ref(n)                => n
    case Expr.Null                  => "null"
    case Expr.This                  => "this"
    case Expr.Super                 => "super"
    case Expr.Access(e, f)          => s"${emitExprText(e)}.$f"
    case Expr.Index(e, i)           => s"${emitExprText(e)}[${emitExprText(i)}]"
    case Expr.Call(recv, name, args, _) =>
      val argsStr = args.map(emitExprText).mkString(", ")
      recv match
        case Some(r) => s"${emitExprText(r)}.$name($argsStr)"
        case None    => s"$name($argsStr)"
    case Expr.New(t, args) =>
      s"new ${tm.typeName(t)}(${args.map(emitExprText).mkString(", ")})"
    case Expr.BinOp(l, op, r) =>
      s"(${emitExprText(l)} ${binOpStr(op)} ${emitExprText(r)})"
    case Expr.UnOp(op, e) =>
      s"${unOpStr(op)}${emitExprText(e)}"
    case Expr.Ternary(c, t, f) =>
      s"(${emitExprText(c)} ? ${emitExprText(t)} : ${emitExprText(f)})"
    case Expr.Cast(e, t) =>
      s"((${tm.typeName(t)}) ${emitExprText(e)})"
    case Expr.Lambda(ps, body) =>
      val params = ps.map(_.name).mkString(", ")
      val bodyStr = body.stmts match
        case Vector(Stmt.Return(Some(e))) => emitExprText(e)
        case Vector(Stmt.Eval(e))         => emitExprText(e)
        case stmts                        => s"{ ${stmts.map(s => emitStmtText(s)).mkString("; ")} }"
      s"($params) -> $bodyStr"
    case Expr.TypeRef(t) => tm.typeName(t)

  private def emitStmtText(s: Stmt): String = s match
    case Stmt.Return(Some(e)) => s"return ${emitExprText(e)}"
    case Stmt.Return(None)    => "return"
    case Stmt.Eval(e)         => emitExprText(e)
    case Stmt.Let(n, t, init, _) =>
      val initStr = init.map(e => s" = ${emitExprText(e)}").getOrElse("")
      s"${tm.typeName(t)} $n$initStr"
    case Stmt.Assign(target, value) => s"${emitExprText(target)} = ${emitExprText(value)}"
    case Stmt.Throw(e)        => s"throw ${emitExprText(e)}"
    case _                    => s"/* unsupported: ${s.getClass.getSimpleName} */"

  // -- Signature builders -------------------------------------------------

  private def typeSignature(td: Decl.TypeDecl): String =
    val vis = visStr(td.visibility)
    val typeParams = emitTypeParams(td.typeParams)
    val (keyword, kindMod) = td.kind match
      case TypeKind.Protocol  => ("interface", "")
      case TypeKind.Abstract  => ("class", "abstract ")
      case TypeKind.Singleton => ("class", "final ")
      case _                  => ("class", "")
    val ext = td.kind match
      case TypeKind.Protocol =>
        if td.supertypes.nonEmpty then s" extends ${td.supertypes.map(tm.typeName).mkString(", ")}" else ""
      case _ =>
        val (superclass, ifaces) = td.supertypes.splitAt(1)
        val extStr = superclass.headOption.map(s => s" extends ${tm.typeName(s)}").getOrElse("")
        val implStr = if ifaces.nonEmpty then s" implements ${ifaces.map(tm.typeName).mkString(", ")}" else ""
        extStr + implStr
    s"${vis}${kindMod}$keyword ${td.name}$typeParams$ext"

  private def funcSignature(f: Func, parentKind: TypeKind): String =
    val vis = visStr(f.visibility)
    val staticMod = if f.modifiers.contains(FuncModifier.Static) then "static " else ""
    val typeParams = emitTypeParams(f.typeParams)
    val ret = tm.typeName(f.returnType)
    val params = f.params.map(p => s"${tm.typeName(p.paramType)} ${p.name}").mkString(", ")
    val isAbstract = f.body.isEmpty && !f.modifiers.contains(FuncModifier.Default)
    val isDefault = f.modifiers.contains(FuncModifier.Default) && f.body.isDefined && parentKind == TypeKind.Protocol

    if isAbstract && parentKind != TypeKind.Protocol then
      s"${vis}abstract $ret$typeParams ${f.name}($params)"
    else if isDefault then
      s"${vis}default $ret$typeParams ${f.name}($params)"
    else
      s"${vis}${staticMod}$ret$typeParams ${f.name}($params)"

  // -- Helpers ------------------------------------------------------------

  private def emitField(f: Field): CodeNode =
    val vis = visStr(f.visibility)
    val fin = if f.mutability == Mutability.Immutable then "final " else ""
    val init = f.defaultValue.map(v => s" = ${emitExprText(v)}").getOrElse("")
    val annots = f.annotations.map(a => CodeNode.Line(s"@${a.name}"))
    val fieldLine = CodeNode.Line(s"${vis}${fin}${tm.typeName(f.fieldType)} ${f.name}$init;")
    if annots.isEmpty then fieldLine
    else CodeNode.Block(annots :+ fieldLine)

  private def emitConstDecl(cd: Decl.ConstDecl): CodeNode =
    val vis = visStr(cd.visibility)
    CodeNode.Line(s"${vis}static final ${tm.typeName(cd.constType)} ${cd.name} = ${emitExprText(cd.value)};")

  private def visStr(v: Visibility): String = v match
    case Visibility.Public         => "public "
    case Visibility.Private        => "private "
    case Visibility.Protected      => "protected "
    case Visibility.Internal       => ""
    case Visibility.PackagePrivate => ""

  private def emitTypeParams(tps: Vector[TypeParam]): String =
    if tps.isEmpty then ""
    else
      val params = tps.map { tp =>
        if tp.upperBounds.nonEmpty then s"${tp.name} extends ${tp.upperBounds.map(tm.typeName).mkString(" & ")}"
        else tp.name
      }
      s"<${params.mkString(", ")}>"

  private def binOpStr(op: BinaryOp): String = op match
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

  private def unOpStr(op: UnaryOp): String = op match
    case UnaryOp.Not    => "!"
    case UnaryOp.Negate => "-"
    case UnaryOp.BitNot => "~"

object JavaEmitter:
  def apply[F[_]: Monad]: JavaEmitter[F] = new JavaEmitter[F]
