package io.alnovis.ircraft.dialect.semantic.expr

/** Extension methods for traversing and transforming Expression, Statement, and Block trees. */
object ExprTraversal:

  // ── Expression ───────────────────────────────────────────────────────

  extension (expr: Expression)

    /** Visit this expression and all sub-expressions depth-first. */
    def walk(f: Expression => Unit): Unit =
      f(expr)
      expr match
        case Expression.MethodCall(recv, _, args, _) =>
          recv.foreach(_.walk(f))
          args.foreach(_.walk(f))
        case Expression.FieldAccess(recv, _)    => recv.walk(f)
        case Expression.NewInstance(_, args)    => args.foreach(_.walk(f))
        case Expression.Cast(e, _)              => e.walk(f)
        case Expression.Conditional(cond, t, e) => cond.walk(f); t.walk(f); e.walk(f)
        case Expression.BinaryOp(l, _, r)       => l.walk(f); r.walk(f)
        case Expression.UnaryOp(_, e)           => e.walk(f)
        case Expression.Lambda(_, body)         => body.walk(f)
        case _                                  => ()

    /** Collect matching values from this expression and all sub-expressions. */
    def collectAll[A](pf: PartialFunction[Expression, A]): List[A] =
      val self = pf.lift(expr).toList
      val nested: List[A] = expr match
        case Expression.MethodCall(recv, _, args, _) =>
          recv.toList.flatMap(_.collectAll(pf)) ++ args.flatMap(_.collectAll(pf))
        case Expression.FieldAccess(recv, _)    => recv.collectAll(pf)
        case Expression.NewInstance(_, args)    => args.flatMap(_.collectAll(pf))
        case Expression.Cast(e, _)              => e.collectAll(pf)
        case Expression.Conditional(cond, t, e) => cond.collectAll(pf) ++ t.collectAll(pf) ++ e.collectAll(pf)
        case Expression.BinaryOp(l, _, r)       => l.collectAll(pf) ++ r.collectAll(pf)
        case Expression.UnaryOp(_, e)           => e.collectAll(pf)
        case Expression.Lambda(_, body)         => body.collectAll(pf)
        case _                                  => Nil
      self ++ nested

    /** Deep transform: apply f bottom-up to all sub-expressions, then to self. */
    def transform(f: PartialFunction[Expression, Expression]): Expression =
      val transformed = expr match
        case m @ Expression.MethodCall(recv, name, args, typeArgs) =>
          m.copy(receiver = recv.map(_.transform(f)), args = args.map(_.transform(f)))
        case fa @ Expression.FieldAccess(recv, name) => fa.copy(receiver = recv.transform(f))
        case ni @ Expression.NewInstance(t, args)    => ni.copy(args = args.map(_.transform(f)))
        case c @ Expression.Cast(e, t)               => c.copy(expr = e.transform(f))
        case c @ Expression.Conditional(cond, t, e) =>
          c.copy(cond = cond.transform(f), thenExpr = t.transform(f), elseExpr = e.transform(f))
        case b @ Expression.BinaryOp(l, op, r)     => b.copy(left = l.transform(f), right = r.transform(f))
        case u @ Expression.UnaryOp(op, e)         => u.copy(expr = e.transform(f))
        case lam @ Expression.Lambda(params, body) => lam.copy(body = body.transform(f))
        case other                                 => other
      f.lift(transformed).getOrElse(transformed)

  // ── Statement ────────────────────────────────────────────────────────

  extension (stmt: Statement)

    /** Visit all expressions in this statement (including nested blocks). */
    def walkExprs(f: Expression => Unit): Unit = stmt match
      case Statement.ExpressionStmt(expr)      => expr.walk(f)
      case Statement.ReturnStmt(value)         => value.foreach(_.walk(f))
      case Statement.VarDecl(_, _, init, _)    => init.foreach(_.walk(f))
      case Statement.Assignment(target, value) => target.walk(f); value.walk(f)
      case Statement.IfStmt(cond, thenBlock, elseBlock) =>
        cond.walk(f); thenBlock.walkExprs(f); elseBlock.foreach(_.walkExprs(f))
      case Statement.ForEachStmt(_, _, iterable, body) =>
        iterable.walk(f); body.walkExprs(f)
      case Statement.ThrowStmt(expr) => expr.walk(f)
      case Statement.TryCatchStmt(tryBlock, catches, fin) =>
        tryBlock.walkExprs(f); catches.foreach(_.body.walkExprs(f)); fin.foreach(_.walkExprs(f))

    /** Collect matching values from all expressions in this statement. */
    def collectExprs[A](pf: PartialFunction[Expression, A]): List[A] = stmt match
      case Statement.ExpressionStmt(expr)      => expr.collectAll(pf)
      case Statement.ReturnStmt(value)         => value.toList.flatMap(_.collectAll(pf))
      case Statement.VarDecl(_, _, init, _)    => init.toList.flatMap(_.collectAll(pf))
      case Statement.Assignment(target, value) => target.collectAll(pf) ++ value.collectAll(pf)
      case Statement.IfStmt(cond, thenBlock, elseBlock) =>
        cond.collectAll(pf) ++ thenBlock.collectExprs(pf) ++ elseBlock.toList.flatMap(_.collectExprs(pf))
      case Statement.ForEachStmt(_, _, iterable, body) =>
        iterable.collectAll(pf) ++ body.collectExprs(pf)
      case Statement.ThrowStmt(expr) => expr.collectAll(pf)
      case Statement.TryCatchStmt(tryBlock, catches, fin) =>
        tryBlock.collectExprs(pf) ++ catches.flatMap(_.body.collectExprs(pf)) ++ fin.toList.flatMap(_.collectExprs(pf))

    /** Transform all expressions in this statement (including nested blocks). Bottom-up. */
    def transformExprs(f: PartialFunction[Expression, Expression]): Statement = stmt match
      case Statement.ExpressionStmt(expr)      => Statement.ExpressionStmt(expr.transform(f))
      case Statement.ReturnStmt(value)         => Statement.ReturnStmt(value.map(_.transform(f)))
      case Statement.VarDecl(n, t, init, fin)  => Statement.VarDecl(n, t, init.map(_.transform(f)), fin)
      case Statement.Assignment(target, value) => Statement.Assignment(target.transform(f), value.transform(f))
      case Statement.IfStmt(cond, thenBlock, elseBlock) =>
        Statement.IfStmt(cond.transform(f), thenBlock.transformExprs(f), elseBlock.map(_.transformExprs(f)))
      case Statement.ForEachStmt(v, t, iterable, body) =>
        Statement.ForEachStmt(v, t, iterable.transform(f), body.transformExprs(f))
      case Statement.ThrowStmt(expr) => Statement.ThrowStmt(expr.transform(f))
      case Statement.TryCatchStmt(tryBlock, catches, fin) =>
        Statement.TryCatchStmt(
          tryBlock.transformExprs(f),
          catches.map(c => c.copy(body = c.body.transformExprs(f))),
          fin.map(_.transformExprs(f))
        )

  // ── Block ────────────────────────────────────────────────────────────

  extension (block: Block)

    /** Visit all expressions in all statements of this block. */
    def walkExprs(f: Expression => Unit): Unit =
      block.statements.foreach(_.walkExprs(f))

    /** Collect matching values from all expressions in this block. */
    def collectExprs[A](pf: PartialFunction[Expression, A]): List[A] =
      block.statements.flatMap(_.collectExprs(pf))

    /** Transform all expressions in all statements of this block. */
    def transformExprs(f: PartialFunction[Expression, Expression]): Block =
      Block(block.statements.map(_.transformExprs(f)))

    /** Visit all statements in this block (including nested blocks). */
    def walkStmts(f: Statement => Unit): Unit =
      block.statements.foreach: stmt =>
        f(stmt)
        stmt match
          case Statement.IfStmt(_, thenBlock, elseBlock) =>
            thenBlock.walkStmts(f); elseBlock.foreach(_.walkStmts(f))
          case Statement.ForEachStmt(_, _, _, body) => body.walkStmts(f)
          case Statement.TryCatchStmt(tryBlock, catches, fin) =>
            tryBlock.walkStmts(f); catches.foreach(_.body.walkStmts(f)); fin.foreach(_.walkStmts(f))
          case _ => ()

    /** Collect matching values from all statements (including nested blocks). */
    def collectStmts[A](pf: PartialFunction[Statement, A]): List[A] =
      block.statements.flatMap: stmt =>
        val self = pf.lift(stmt).toList
        val nested: List[A] = stmt match
          case Statement.IfStmt(_, thenBlock, elseBlock) =>
            thenBlock.collectStmts(pf) ++ elseBlock.toList.flatMap(_.collectStmts(pf))
          case Statement.ForEachStmt(_, _, _, body) => body.collectStmts(pf)
          case Statement.TryCatchStmt(tryBlock, catches, fin) =>
            tryBlock.collectStmts(pf) ++ catches.flatMap(_.body.collectStmts(pf)) ++ fin.toList.flatMap(
              _.collectStmts(pf)
            )
          case _ => Nil
        self ++ nested

    /** Transform all statements in this block (top-level only, not nested). */
    def transformStmts(f: PartialFunction[Statement, Statement]): Block =
      Block(block.statements.map(s => f.lift(s).getOrElse(s)))
