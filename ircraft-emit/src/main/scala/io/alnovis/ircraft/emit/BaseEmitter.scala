package io.alnovis.ircraft.emit

import cats.*
import cats.data.Kleisli
import cats.syntax.all.*
import java.nio.file.Path
import scala.collection.immutable.SortedMap
import io.alnovis.ircraft.core.ir.*

/** Emitter = Kleisli[F, Module, Map[Path, String]]. Composable with Pass and Lowering. */
type Emitter[F[_]] = Kleisli[F, Module, Map[Path, String]]

/**
  * Generic two-phase emitter parameterized by LanguageSyntax.
  * Phase 1: Semantic IR -> CodeNode tree (via syntax)
  * Phase 2: CodeNode -> String (via Renderer, pure)
  *
  * Subclasses only provide `syntax` and `tm`. All traversal is here.
  */
abstract class BaseEmitter[F[_]: Monad]:

  protected def syntax: LanguageSyntax
  protected def tm: TypeMapping

  final val emitter: Emitter[F] = Kleisli(apply)

  final def apply(module: Module): F[Map[Path, String]] =
    module.units
      .flatTraverse { unit =>
        unit.declarations.traverse { decl =>
          emitFileTree(unit.namespace, decl).map { tree =>
            val name   = decl.name
            val path   = Path.of(unit.namespace.replace('.', '/'), s"$name.${syntax.fileExtension}")
            val source = Renderer.render(tree, syntax.statementTerminator)
            (path, source)
          }
        }
      }
      .map { pairs =>
        val filtered = pairs.filter((_, source) => source.trim.nonEmpty)
        SortedMap.from(filtered)(using Ordering.by(_.toString))
      }

  def toFileTree(namespace: String, decl: Decl): F[CodeNode] = emitFileTree(namespace, decl)
  def toDeclTree(decl: Decl): F[CodeNode]                    = emitDeclTree(decl)

  // -- File tree -----------------------------------------------------------

  protected def emitFileTree(namespace: String, decl: Decl): F[CodeNode] =
    emitDeclTree(decl).map { declTree =>
      val imports = ImportCollector.collect(decl, tm)
      CodeNode.File(syntax.packageDecl(namespace), imports.toVector, Vector(declTree))
    }

  // -- Declarations --------------------------------------------------------

  protected def emitDeclTree(decl: Decl): F[CodeNode] = decl match
    case td: Decl.TypeDecl  => emitTypeDecl(td)
    case ed: Decl.EnumDecl  => emitEnumDecl(ed)
    case fd: Decl.FuncDecl  => emitFuncNode(fd.func, TypeKind.Product)
    case cd: Decl.ConstDecl => Monad[F].pure(emitConstDecl(cd))
    case _: Decl.AliasDecl  => Monad[F].pure(CodeNode.Line(""))

  private def emitTypeDecl(td: Decl.TypeDecl): F[CodeNode] =
    // Protocol (interface/trait) should not have fields -- only abstract methods
    val fieldNodes = if td.kind == TypeKind.Protocol then Vector.empty else td.fields.map(emitField)
    for
      funcNodes   <- td.functions.traverse(f => emitFuncNode(f, td.kind))
      nestedNodes <- td.nested.traverse(emitDeclTree)
    yield
      val vis            = syntax.visibility(td.visibility)
      val typeParams     = syntax.typeParamList(td.typeParams, tm)
      val supertypeNames = td.supertypes.map(tm.typeName)
      val sig            = syntax.typeSignature(vis, td.kind, td.name, typeParams, supertypeNames)
      val sections       = Vector(fieldNodes, funcNodes, nestedNodes).filter(_.nonEmpty)
      val typeBlock      = CodeNode.TypeBlock(sig, sections)
      wrapWithDocAndAnnotations(td.meta, td.annotations, typeBlock)

  private def emitEnumDecl(ed: Decl.EnumDecl): F[CodeNode] =
    for funcNodes <- ed.functions.traverse(f => emitFuncNode(f, TypeKind.Product))
    yield
      val vis            = syntax.visibility(ed.visibility)
      val supertypeNames = ed.supertypes.map(tm.typeName)
      val hasValues      = ed.variants.exists(_.args.nonEmpty)
      val sig            = syntax.enumSignature(vis, ed.name, supertypeNames, hasValues)

      val constantSection =
        if ed.variants.isEmpty then Vector.empty
        else
          val lastIdx = ed.variants.size - 1
          ed.variants.zipWithIndex.map { (v, idx) =>
            val args = if v.args.nonEmpty then s"(${v.args.map(emitExprText).mkString(", ")})" else ""
            CodeNode.Line(syntax.enumVariant(v.name, args, idx == lastIdx, ed.name))
          }

      val sections  = Vector(constantSection, funcNodes).filter(_.nonEmpty)
      val enumBlock = CodeNode.TypeBlock(sig, sections)
      wrapWithDocAndAnnotations(ed.meta, ed.annotations, enumBlock)

  // -- Functions -----------------------------------------------------------

  private def emitFuncNode(f: Func, parentKind: TypeKind): F[CodeNode] =
    // Only transform public API method names (not protected/private internal methods)
    val name =
      if f.visibility == Visibility.Public || f.visibility == Visibility.PackagePrivate
      then syntax.transformMethodName(f.name)
      else f.name
    val vis        = syntax.visibility(f.visibility)
    val typeParams = syntax.typeParamList(f.typeParams, tm)
    val ret        = tm.typeName(f.returnType)
    val params     = f.params.map(p => syntax.paramDecl(p.name, tm.typeName(p.paramType))).mkString(", ")
    val isAbstract = f.body.isEmpty && !f.modifiers.contains(FuncModifier.Default)
    val sig        = syntax.funcSignature(vis, f.modifiers, typeParams, ret, name, params, isAbstract, parentKind)

    f.body match
      case None =>
        val node = CodeNode.Func(sig, None)
        Monad[F].pure(wrapWithDocAndAnnotations(f.meta, f.annotations, node))
      case Some(body) =>
        body.stmts.traverse(emitStmtNode).map { stmtNodes =>
          val funcNode =
            if syntax.useFuncEqualsStyle && stmtNodes.size == 1 then
              stmtNodes.head match
                case CodeNode.Line(text) => CodeNode.Line(s"$sig = $text")
                case _                   => CodeNode.Func(sig, Some(stmtNodes))
            else CodeNode.Func(sig, Some(stmtNodes))
          wrapWithDocAndAnnotations(f.meta, f.annotations, funcNode)
        }

  // -- Statements ----------------------------------------------------------

  private def emitStmtNode(s: Stmt): F[CodeNode] = s match
    case Stmt.Return(Some(e)) => Monad[F].pure(CodeNode.Line(syntax.returnStmt(emitExprText(e))))
    case Stmt.Return(None)    => Monad[F].pure(CodeNode.Line(syntax.returnVoid))
    case Stmt.Eval(e)         => Monad[F].pure(CodeNode.Line(s"${emitExprText(e)}${syntax.statementTerminator}"))
    case Stmt.Let(n, t, init, mut) =>
      Monad[F].pure(CodeNode.Line(syntax.letStmt(mut, tm.typeName(t), n, init.map(emitExprText))))
    case Stmt.Assign(target, value) =>
      Monad[F].pure(CodeNode.Line(syntax.assignStmt(emitExprText(target), emitExprText(value))))
    case Stmt.If(cond, thenBody, elseBody) =>
      for
        thenNodes <- thenBody.stmts.traverse(emitStmtNode)
        elseNodes <- elseBody.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.IfElse(emitExprText(cond), thenNodes, elseNodes)
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
              if syntax.supportsNativeMatch
              then syntax.matchCaseHeader(s"$patternStr$guardStr")
              else s"// case $patternStr$guardStr" // fallback comment
            (header, bodyNodes)
          }
        }
        .map { renderedCases =>
          if syntax.supportsNativeMatch then CodeNode.MatchBlock(syntax.matchHeader(emitExprText(expr)), renderedCases)
          else
            // fallback: render as if-chain for languages without pattern matching
            emitMatchAsIfChain(expr, cases)
        }
    case Stmt.Switch(expr, cases, default) =>
      for
        caseNodes <- cases.traverse(sc =>
          sc.body.stmts.traverse(emitStmtNode).map(ns => (emitExprText(sc.pattern), ns))
        )
        defaultNodes <- default.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.SwitchBlock(emitExprText(expr), caseNodes, defaultNodes)
    case Stmt.Throw(e)   => Monad[F].pure(CodeNode.Line(syntax.throwStmt(emitExprText(e))))
    case Stmt.Comment(t) => Monad[F].pure(CodeNode.Comment(t))
    case Stmt.TryCatch(tryBody, catches, finallyBody) =>
      for
        tryNodes <- tryBody.stmts.traverse(emitStmtNode)
        catchNodes <- catches.traverse(c =>
          c.body.stmts.traverse(emitStmtNode).map(ns => (s"${tm.typeName(c.exType)} ${c.name}", ns))
        )
        finallyNodes <- finallyBody.traverse(_.stmts.traverse(emitStmtNode))
      yield CodeNode.TryCatch(tryNodes, catchNodes, finallyNodes)

  // -- Expressions ---------------------------------------------------------

  protected def emitExprText(expr: Expr): String = expr match
    case Expr.Lit(v, _)    => v
    case Expr.Ref(n)       => n
    case Expr.Null         => syntax.nullLiteral
    case Expr.This         => syntax.thisLiteral
    case Expr.Super        => syntax.superLiteral
    case Expr.Access(e, f) => s"${emitExprText(e)}.$f"
    case Expr.Index(e, i)  => s"${emitExprText(e)}(${emitExprText(i)})"
    case Expr.Call(recv, name, args, _) =>
      val argsStr = args.map(emitExprText).mkString(", ")
      recv match
        case Some(r) => s"${emitExprText(r)}.$name($argsStr)"
        case None    => s"$name($argsStr)"
    case Expr.New(t, args)     => syntax.newExpr(tm.typeName(t), args.map(emitExprText).mkString(", "))
    case Expr.BinOp(l, op, r)  => s"(${emitExprText(l)} ${syntax.binOp(op)} ${emitExprText(r)})"
    case Expr.UnOp(op, e)      => s"${syntax.unaryOp(op)}${emitExprText(e)}"
    case Expr.Ternary(c, t, f) => syntax.ternaryExpr(emitExprText(c), emitExprText(t), emitExprText(f))
    case Expr.Cast(e, t)       => syntax.castExpr(emitExprText(e), tm.typeName(t))
    case Expr.Lambda(ps, body) =>
      val params = ps.map(_.name).mkString(", ")
      val bodyStr = body.stmts match
        case Vector(Stmt.Return(Some(e))) => emitExprText(e)
        case Vector(Stmt.Eval(e))         => emitExprText(e)
        case stmts                        => s"{ ${stmts.map(emitStmtInline).mkString("; ")} }"
      syntax.lambdaExpr(params, bodyStr)
    case Expr.TypeRef(t) => tm.typeName(t)

  private def emitStmtInline(s: Stmt): String = s match
    case Stmt.Return(Some(e)) => syntax.returnStmt(emitExprText(e)).stripSuffix(syntax.statementTerminator)
    case Stmt.Return(None)    => syntax.returnVoid.stripSuffix(syntax.statementTerminator)
    case Stmt.Eval(e)         => emitExprText(e)
    case Stmt.Assign(target, value) =>
      syntax.assignStmt(emitExprText(target), emitExprText(value)).stripSuffix(syntax.statementTerminator)
    case Stmt.Throw(e) => syntax.throwStmt(emitExprText(e)).stripSuffix(syntax.statementTerminator)
    case _             => s"/* unsupported: ${s.getClass.getSimpleName} */"

  // -- Fields --------------------------------------------------------------

  private def emitField(f: Field): CodeNode =
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

  private def emitConstDecl(cd: Decl.ConstDecl): CodeNode =
    val vis = syntax.visibility(cd.visibility)
    CodeNode.Line(syntax.constDecl(vis, tm.typeName(cd.constType), cd.name, emitExprText(cd.value)))

  /** Prepend doc comment and annotations before a CodeNode. */
  private def wrapWithDocAndAnnotations(meta: Meta, annotations: Vector[Annotation], node: CodeNode): CodeNode =
    val prefix = Vector.newBuilder[CodeNode]
    // Doc rendered by syntax -- already formatted, emit as raw lines
    meta.get(Doc.key).foreach { doc =>
      val rendered = syntax.renderDoc(doc)
      rendered.split("\n").foreach(line => prefix += CodeNode.Line(line))
    }
    annotations.foreach(a => prefix += CodeNode.Line(s"@${a.name}"))
    val parts = prefix.result()
    if parts.isEmpty then node
    else CodeNode.Block(parts :+ node)

  // -- Pattern matching helpers -------------------------------------------

  private def emitPattern(p: Pattern): String = p match
    case Pattern.TypeTest(name, typeExpr) => syntax.patternTypeTest(name, tm.typeName(typeExpr))
    case Pattern.Literal(value)           => syntax.patternLiteral(emitExprText(value))
    case Pattern.Binding(name)            => name
    case Pattern.Wildcard                 => syntax.patternWildcard

  /** Fallback for languages without pattern matching: render Match as if-chain. */
  private def emitMatchAsIfChain(expr: Expr, cases: Vector[MatchCase]): CodeNode =
    val nodes = cases.zipWithIndex.map { (mc, idx) =>
      val cond = mc.pattern match
        case Pattern.TypeTest(name, typeExpr) =>
          s"${emitExprText(expr)} instanceof ${tm.typeName(typeExpr)}"
        case Pattern.Literal(value) =>
          s"${emitExprText(expr)} == ${emitExprText(value)}"
        case Pattern.Wildcard | Pattern.Binding(_) =>
          "true" // default case
      val guardStr = mc.guard.map(g => s" && ${emitExprText(g)}").getOrElse("")
      // Simplify: each case as an if block with body
      val bodyNodes = mc.body.stmts.map { s =>
        s match
          case Stmt.Return(Some(e)) => CodeNode.Line(syntax.returnStmt(emitExprText(e)))
          case Stmt.Eval(e)         => CodeNode.Line(s"${emitExprText(e)}${syntax.statementTerminator}")
          case _                    => CodeNode.Line(s"/* match case $idx */")
      }
      CodeNode.IfElse(s"$cond$guardStr", bodyNodes, None)
    }
    CodeNode.Block(nodes)
