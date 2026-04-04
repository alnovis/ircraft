package io.alnovis.ircraft.emit

import io.alnovis.ircraft.core.ir._

object ImportCollector {

  def collect(decl: Decl, tm: TypeMapping): Set[String] = decl match {
    case td: Decl.TypeDecl  => collectTypeDecl(td, tm)
    case ed: Decl.EnumDecl  => collectEnumDecl(ed, tm)
    case fd: Decl.FuncDecl  => collectFunc(fd.func, tm)
    case ad: Decl.AliasDecl => tm.imports(ad.target)
    case cd: Decl.ConstDecl => tm.imports(cd.constType) ++ collectExpr(cd.value, tm)
  }

  private def collectTypeDecl(td: Decl.TypeDecl, tm: TypeMapping): Set[String] =
    td.supertypes.flatMap(tm.imports).toSet ++
      td.fields.flatMap(f => collectField(f, tm)) ++
      td.functions.flatMap(f => collectFunc(f, tm)) ++
      td.nested.flatMap(n => collect(n, tm)) ++
      td.typeParams.flatMap(_.upperBounds.flatMap(tm.imports))

  private def collectEnumDecl(ed: Decl.EnumDecl, tm: TypeMapping): Set[String] =
    ed.supertypes.flatMap(tm.imports).toSet ++
      ed.functions.flatMap(f => collectFunc(f, tm)) ++
      ed.variants.flatMap(v => v.args.flatMap(e => collectExpr(e, tm)) ++ v.fields.flatMap(f => collectField(f, tm)))

  private def collectField(f: Field, tm: TypeMapping): Set[String] =
    tm.imports(f.fieldType) ++ f.defaultValue.toSet.flatMap((e: Expr) => collectExpr(e, tm))

  private def collectFunc(f: Func, tm: TypeMapping): Set[String] =
    tm.imports(f.returnType) ++
      f.params.flatMap(p => tm.imports(p.paramType)) ++
      f.body.toSet.flatMap((b: Body) => collectBody(b, tm)) ++
      f.typeParams.flatMap(_.upperBounds.flatMap(tm.imports))

  private def collectBody(b: Body, tm: TypeMapping): Set[String] =
    b.stmts.flatMap(s => collectStmt(s, tm)).toSet

  private def collectStmt(s: Stmt, tm: TypeMapping): Set[String] = s match {
    case Stmt.Eval(e)               => collectExpr(e, tm)
    case Stmt.Return(Some(e))       => collectExpr(e, tm)
    case Stmt.Return(None)          => Set.empty
    case Stmt.Let(_, t, init, _)    => tm.imports(t) ++ init.toSet.flatMap((e: Expr) => collectExpr(e, tm))
    case Stmt.Assign(target, value) => collectExpr(target, tm) ++ collectExpr(value, tm)
    case Stmt.If(c, tb, eb) =>
      collectExpr(c, tm) ++ collectBody(tb, tm) ++ eb.toSet.flatMap((b: Body) => collectBody(b, tm))
    case Stmt.ForEach(_, t, iter, body) => tm.imports(t) ++ collectExpr(iter, tm) ++ collectBody(body, tm)
    case Stmt.Throw(e)                  => collectExpr(e, tm)
    case Stmt.While(c, body)            => collectExpr(c, tm) ++ collectBody(body, tm)
    case Stmt.Switch(e, cases, dflt) =>
      collectExpr(e, tm) ++
        cases.flatMap(sc => collectExpr(sc.pattern, tm) ++ collectBody(sc.body, tm)) ++
        dflt.toSet.flatMap((b: Body) => collectBody(b, tm))
    case Stmt.Match(e, cases) =>
      collectExpr(e, tm) ++
        cases.flatMap(mc =>
          collectPattern(mc.pattern, tm) ++ mc.guard.toSet.flatMap((g: Expr) => collectExpr(g, tm)) ++ collectBody(
            mc.body,
            tm
          )
        )
    case Stmt.Comment(_) => Set.empty
    case Stmt.TryCatch(tb, cs, fb) =>
      collectBody(tb, tm) ++
        cs.flatMap(c => tm.imports(c.exType) ++ collectBody(c.body, tm)) ++
        fb.toSet.flatMap((b: Body) => collectBody(b, tm))
  }

  private def collectPattern(p: Pattern, tm: TypeMapping): Set[String] = p match {
    case Pattern.TypeTest(_, t) => tm.imports(t)
    case Pattern.Literal(e)     => collectExpr(e, tm)
    case _                      => Set.empty
  }

  private def collectExpr(e: Expr, tm: TypeMapping): Set[String] = e match {
    case Expr.Lit(_, t)    => tm.imports(t)
    case Expr.New(t, args) => tm.imports(t) ++ args.flatMap(a => collectExpr(a, tm))
    case Expr.Cast(ex, t)  => tm.imports(t) ++ collectExpr(ex, tm)
    case Expr.Call(r, _, args, ta) =>
      r.toSet.flatMap((e: Expr) => collectExpr(e, tm)) ++ args.flatMap(a => collectExpr(a, tm)) ++ ta.flatMap(
        tm.imports
      )
    case Expr.Access(ex, _)    => collectExpr(ex, tm)
    case Expr.Index(ex, idx)   => collectExpr(ex, tm) ++ collectExpr(idx, tm)
    case Expr.BinOp(l, _, r)   => collectExpr(l, tm) ++ collectExpr(r, tm)
    case Expr.UnOp(_, ex)      => collectExpr(ex, tm)
    case Expr.Ternary(c, t, f) => collectExpr(c, tm) ++ collectExpr(t, tm) ++ collectExpr(f, tm)
    case Expr.Lambda(ps, body) => ps.flatMap(p => tm.imports(p.paramType)).toSet ++ collectBody(body, tm)
    case Expr.TypeRef(t)       => tm.imports(t)
    case _                     => Set.empty
  }
}
