package io.alnovis.ircraft.dialect.semantic

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.expr.*
import io.alnovis.ircraft.dialect.semantic.expr.ExprTraversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/**
  * Bridge between Operation-level traversal and Expression-level traversal.
  *
  * Provides Module-level methods to reach into method/constructor bodies.
  */
object BodyTraversal:

  extension (module: Module)

    /** Collect matching values from all expressions in all method/constructor bodies. */
    def collectFromBodies[A](pf: PartialFunction[Expression, A]): Vector[A] =
      val builder = Vector.newBuilder[A]
      module.walkAll:
        case m: MethodOp      => m.body.foreach(b => builder ++= b.collectExprs(pf))
        case c: ConstructorOp => c.body.foreach(b => builder ++= b.collectExprs(pf))
        case _                => ()
      builder.result()

    /** Transform all expressions in all method/constructor bodies across the module. */
    def transformBodies(f: PartialFunction[Expression, Expression]): Module =
      module.transform:
        case m: MethodOp if m.body.isDefined =>
          m.copy(body = m.body.map(_.transformExprs(f)))
        case c: ConstructorOp if c.body.isDefined =>
          c.copy(body = c.body.map(_.transformExprs(f)))
