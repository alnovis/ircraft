package io.alnovis.ircraft.emit

import cats._
import cats.data.Kleisli
import cats.syntax.all._
import java.nio.file.Path
import scala.collection.immutable.SortedMap
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

abstract class BaseEmitter[F[_]: Monad] {

  protected def syntax: LanguageSyntax
  protected def tm: TypeMapping

  final def apply(module: Module[Fix[SemanticF]]): F[Map[Path, String]] = {
    module.units
      .flatTraverse { unit =>
        unit.declarations.traverse { decl =>
          emitFileTree(unit.namespace, decl).map { tree =>
            val name   = SemanticF.name(decl.unfix)
            val path   = Path.of(unit.namespace.replace('.', '/'), s"$name.${syntax.fileExtension}")
            val source = Renderer.render(tree, syntax.statementTerminator)
            (path, source)
          }
        }
      }
      .map { pairs =>
        val filtered                         = pairs.filter { case (_, source) => source.trim.nonEmpty }
        implicit val pathOrd: Ordering[Path] = Ordering.by(_.toString)
        (SortedMap.newBuilder[Path, String] ++= filtered).result()
      }
  }

  def toFileTree(namespace: String, decl: Fix[SemanticF]): F[CodeNode] = emitFileTree(namespace, decl)
  def toDeclTree(decl: Fix[SemanticF]): F[CodeNode]                    = emitDeclTree(decl)

  protected def emitFileTree(namespace: String, decl: Fix[SemanticF]): F[CodeNode] = {
    emitDeclTree(decl).map { declTree =>
      val imports = ImportCollector.collect(decl, tm)
      CodeNode.File(syntax.packageDecl(namespace), imports.toVector, Vector(declTree))
    }
  }

  protected def emitDeclTree(decl: Fix[SemanticF]): F[CodeNode] = decl.unfix match {
    case td: TypeDeclF[Fix[SemanticF] @unchecked]  => emitTypeDecl(td)
    case ed: EnumDeclF[Fix[SemanticF] @unchecked]  => emitEnumDecl(ed)
    case fd: FuncDeclF[Fix[SemanticF] @unchecked]  => emitFuncNode(fd.func, TypeKind.Product)
    case cd: ConstDeclF[Fix[SemanticF] @unchecked] => Monad[F].pure(emitConstDecl(cd))
    case ad: AliasDeclF[Fix[SemanticF] @unchecked] => emitAliasDecl(ad)
  }

  private def emitTypeDecl(td: TypeDeclF[Fix[SemanticF]]): F[CodeNode] = {
    val fieldNodes = if (td.kind == TypeKind.Protocol) Vector.empty else td.fields.map(emitField)
    for {
      funcNodes   <- td.functions.traverse(f => emitFuncNode(f, td.kind))
      nestedNodes <- td.nested.traverse(emitDeclTree)
    } yield {
      val vis            = syntax.visibility(td.visibility)
      val typeParams     = syntax.typeParamList(td.typeParams, tm)
      val supertypeNames = td.supertypes.map(tm.typeName)
      val sig            = syntax.typeSignature(vis, td.kind, td.name, typeParams, supertypeNames)
      val sections       = Vector(fieldNodes, funcNodes, nestedNodes).filter(_.nonEmpty)
      val typeBlock      = CodeNode.TypeBlock(sig, sections)
      wrapWithDocAndAnnotations(td.meta, td.annotations, typeBlock)
    }
  }

  private def emitEnumDecl(ed: EnumDeclF[Fix[SemanticF]]): F[CodeNode] = {
    for {
      funcNodes <- ed.functions.traverse(f => emitFuncNode(f, TypeKind.Product))
    } yield {
      val vis            = syntax.visibility(ed.visibility)
      val supertypeNames = ed.supertypes.map(tm.typeName)
      val hasValues      = ed.variants.exists(_.args.nonEmpty)
      val sig            = syntax.enumSignature(vis, ed.name, supertypeNames, hasValues)

      val constantSection =
        if (ed.variants.isEmpty) Vector.empty
        else {
          val lastIdx = ed.variants.size - 1
          ed.variants.zipWithIndex.map {
            case (v, idx) =>
              val args = if (v.args.nonEmpty) s"(${v.args.map(emitExprText).mkString(", ")})" else ""
              CodeNode.Line(syntax.enumVariant(v.name, args, idx == lastIdx, ed.name))
          }
        }

      val sections  = Vector(constantSection, funcNodes).filter(_.nonEmpty)
      val enumBlock = CodeNode.TypeBlock(sig, sections)
      wrapWithDocAndAnnotations(ed.meta, ed.annotations, enumBlock)
    }
  }

  private def emitFuncNode(f: Func, parentKind: TypeKind): F[CodeNode] = {
    val name =
      if (f.visibility == Visibility.Public || f.visibility == Visibility.PackagePrivate)
        syntax.transformMethodName(f.name)
      else f.name
    val vis        = syntax.visibility(f.visibility)
    val typeParams = syntax.typeParamList(f.typeParams, tm)
    val ret        = tm.typeName(f.returnType)
    val params     = f.params.map(p => syntax.paramDecl(p.name, tm.typeName(p.paramType))).mkString(", ")
    val isAbstract = f.body.isEmpty && !f.modifiers.contains(FuncModifier.Default)
    val sig        = syntax.funcSignature(vis, f.modifiers, typeParams, ret, name, params, isAbstract, parentKind)

    f.body match {
      case None =>
        val node = CodeNode.Func(sig, None)
        Monad[F].pure(wrapWithDocAndAnnotations(f.meta, f.annotations, node))
      case Some(body) =>
        body.stmts.traverse(emitStmtNode).map { stmtNodes =>
          val funcNode =
            if (syntax.useFuncEqualsStyle && stmtNodes.size == 1) {
              stmtNodes.head match {
                case CodeNode.Line(text) => CodeNode.Line(s"$sig = $text")
                case _                   => CodeNode.Func(sig, Some(stmtNodes))
              }
            } else {
              CodeNode.Func(sig, Some(stmtNodes))
            }
          wrapWithDocAndAnnotations(f.meta, f.annotations, funcNode)
        }
    }
  }

  private def emitStmtNode(s: Stmt): F[CodeNode] = s match {
    case Stmt.Return(Some(e)) => Monad[F].pure(CodeNode.Line(syntax.returnStmt(emitExprText(e))))
    case Stmt.Return(None)    => Monad[F].pure(CodeNode.Line(syntax.returnVoid))
    case Stmt.Eval(e)         => Monad[F].pure(CodeNode.Line(s"${emitExprText(e)}${syntax.statementTerminator}"))
    case Stmt.Let(n, t, init, mut) =>
      Monad[F].pure(CodeNode.Line(syntax.letStmt(mut, tm.typeName(t), n, init.map(emitExprText))))
    case Stmt.Assign(target, value) =>
      Monad[F].pure(CodeNode.Line(syntax.assignStmt(emitExprText(target), emitExprText(value))))
    case Stmt.If(cond, thenBody, elseBody) =>
      for {
        thenNodes <- thenBody.stmts.traverse(emitStmtNode)
        elseNodes <- elseBody.traverse(_.stmts.traverse(emitStmtNode))
      } yield CodeNode.IfElse(emitExprText(cond), thenNodes, elseNodes)
    case Stmt.ForEach(v, t, iter, body) =>
      body.stmts.traverse(emitStmtNode).map { bodyNodes =>
        CodeNode.ForLoop(syntax.forEachHeader(v, tm.typeName(t), emitExprText(iter)), bodyNodes)
      }
    case Stmt.While(cond, body) =>
      body.stmts.traverse(emitStmtNode).map(bodyNodes => CodeNode.WhileLoop(emitExprText(cond), bodyNodes))
    case Stmt.Match(expr, cases) =>
      cases
        .traverse { mc =>
          mc.body.stmts.traverse(emitStmtNode).map { bodyNodes =>
            val patternStr = emitPattern(mc.pattern)
            val guardStr   = mc.guard.map(g => s" if ${emitExprText(g)}").getOrElse("")
            val header =
              if (syntax.supportsNativeMatch) syntax.matchCaseHeader(s"$patternStr$guardStr")
              else s"// case $patternStr$guardStr"
            (header, bodyNodes)
          }
        }
        .flatMap { renderedCases =>
          if (syntax.supportsNativeMatch)
            Monad[F].pure(CodeNode.MatchBlock(syntax.matchHeader(emitExprText(expr)), renderedCases))
          else emitMatchAsIfChain(expr, cases)
        }
    case Stmt.Switch(expr, cases, default) =>
      for {
        caseNodes <- cases.traverse(sc =>
          sc.body.stmts.traverse(emitStmtNode).map(ns => (emitExprText(sc.pattern), ns))
        )
        defaultNodes <- default.traverse(_.stmts.traverse(emitStmtNode))
      } yield CodeNode.SwitchBlock(emitExprText(expr), caseNodes, defaultNodes)
    case Stmt.Throw(e)   => Monad[F].pure(CodeNode.Line(syntax.throwStmt(emitExprText(e))))
    case Stmt.Comment(t) => Monad[F].pure(CodeNode.Comment(t))
    case Stmt.TryCatch(tryBody, catches, finallyBody) =>
      for {
        tryNodes <- tryBody.stmts.traverse(emitStmtNode)
        catchNodes <- catches.traverse(c =>
          c.body.stmts.traverse(emitStmtNode).map(ns => (s"${tm.typeName(c.exType)} ${c.name}", ns))
        )
        finallyNodes <- finallyBody.traverse(_.stmts.traverse(emitStmtNode))
      } yield CodeNode.TryCatch(tryNodes, catchNodes, finallyNodes)
  }

  protected def emitExprText(expr: Expr): String = expr match {
    case Expr.Lit(v, _)    => v
    case Expr.Ref(n)       => n
    case Expr.Null         => syntax.nullLiteral
    case Expr.This         => syntax.thisLiteral
    case Expr.Super        => syntax.superLiteral
    case Expr.Access(e, f) => s"${emitExprText(e)}.$f"
    case Expr.Index(e, i)  => s"${emitExprText(e)}(${emitExprText(i)})"
    case Expr.Call(recv, name, args, _) =>
      val argsStr = args.map(emitExprText).mkString(", ")
      recv match {
        case Some(r) => s"${emitExprText(r)}.$name($argsStr)"
        case None    => s"$name($argsStr)"
      }
    case Expr.New(t, args)     => syntax.newExpr(tm.typeName(t), args.map(emitExprText).mkString(", "))
    case Expr.BinOp(l, op, r)  => s"(${emitExprText(l)} ${syntax.binOp(op)} ${emitExprText(r)})"
    case Expr.UnOp(op, e)      => s"${syntax.unaryOp(op)}${emitExprText(e)}"
    case Expr.Ternary(c, t, f) => syntax.ternaryExpr(emitExprText(c), emitExprText(t), emitExprText(f))
    case Expr.Cast(e, t)       => syntax.castExpr(emitExprText(e), tm.typeName(t))
    case Expr.Lambda(ps, body) =>
      val params = ps.map(_.name).mkString(", ")
      val bodyStr = body.stmts match {
        case Vector(Stmt.Return(Some(e))) => emitExprText(e)
        case Vector(Stmt.Eval(e))         => emitExprText(e)
        case stmts                        => s"{ ${stmts.map(emitStmtInline).mkString("; ")} }"
      }
      syntax.lambdaExpr(params, bodyStr)
    case Expr.TypeRef(t) => tm.typeName(t)
  }

  private def emitStmtInline(s: Stmt): String = s match {
    case Stmt.Return(Some(e)) => syntax.returnStmt(emitExprText(e)).stripSuffix(syntax.statementTerminator)
    case Stmt.Return(None)    => syntax.returnVoid.stripSuffix(syntax.statementTerminator)
    case Stmt.Eval(e)         => emitExprText(e)
    case Stmt.Assign(target, value) =>
      syntax.assignStmt(emitExprText(target), emitExprText(value)).stripSuffix(syntax.statementTerminator)
    case Stmt.Throw(e) => syntax.throwStmt(emitExprText(e)).stripSuffix(syntax.statementTerminator)
    case _             => s"/* unsupported: ${s.getClass.getSimpleName} */"
  }

  private def emitField(f: Field): CodeNode = {
    val name = syntax.transformFieldName(f.name)
    val vis  = syntax.visibility(f.visibility)
    val fieldLine = CodeNode.Line(
      syntax.fieldDecl(
        vis,
        f.mutability == Mutability.Mutable,
        tm.typeName(f.fieldType),
        name,
        f.defaultValue.map(emitExprText)
      )
    )
    wrapWithDocAndAnnotations(f.meta, f.annotations, fieldLine)
  }

  private def emitAliasDecl(ad: AliasDeclF[Fix[SemanticF]]): F[CodeNode] = {
    val vis    = syntax.visibility(ad.visibility)
    val target = tm.typeName(ad.target)
    syntax.aliasDecl(vis, ad.name, target) match {
      case Some(line) => Monad[F].pure(CodeNode.Line(line))
      case None       => Monad[F].pure(CodeNode.Line("")) // unsupported -- filtered out
    }
  }

  private def emitConstDecl(cd: ConstDeclF[Fix[SemanticF]]): CodeNode = {
    val vis = syntax.visibility(cd.visibility)
    CodeNode.Line(syntax.constDecl(vis, tm.typeName(cd.constType), cd.name, emitExprText(cd.value)))
  }

  private def wrapWithDocAndAnnotations(meta: Meta, annotations: Vector[Annotation], node: CodeNode): CodeNode = {
    val prefix = Vector.newBuilder[CodeNode]
    meta.get(Doc.key).foreach { doc =>
      val rendered = syntax.renderDoc(doc)
      rendered.split("\n").foreach(line => prefix += CodeNode.Line(line))
    }
    annotations.foreach(a => prefix += CodeNode.Line(s"@${a.name}"))
    val parts = prefix.result()
    if (parts.isEmpty) node
    else CodeNode.Block(parts :+ node)
  }

  private def emitPattern(p: Pattern): String = p match {
    case Pattern.TypeTest(name, typeExpr) => syntax.patternTypeTest(name, tm.typeName(typeExpr))
    case Pattern.Literal(value)           => syntax.patternLiteral(emitExprText(value))
    case Pattern.Binding(name)            => name
    case Pattern.Wildcard                 => syntax.patternWildcard
  }

  private def emitMatchAsIfChain(expr: Expr, cases: Vector[MatchCase]): F[CodeNode] = {
    cases
      .traverse { mc =>
        val cond = mc.pattern match {
          case Pattern.TypeTest(name, typeExpr) =>
            s"${emitExprText(expr)} instanceof ${tm.typeName(typeExpr)}"
          case Pattern.Literal(value) =>
            s"${emitExprText(expr)} == ${emitExprText(value)}"
          case Pattern.Wildcard | Pattern.Binding(_) =>
            "true"
        }
        val guardStr = mc.guard.map(g => s" && ${emitExprText(g)}").getOrElse("")
        mc.body.stmts.traverse(emitStmtNode).map { bodyNodes =>
          CodeNode.IfElse(s"$cond$guardStr", bodyNodes, None)
        }
      }
      .map(CodeNode.Block(_))
  }
}
