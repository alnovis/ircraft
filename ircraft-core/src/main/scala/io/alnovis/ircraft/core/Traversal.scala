package io.alnovis.ircraft.core

/** Extension methods for IR traversal and transformation. */
object Traversal:

  extension (op: Operation)

    /** Visit this operation and all descendants depth-first. */
    def walk(f: Operation => Unit): Unit =
      f(op)
      op.children.foreach(_.walk(f))

    /** Collect matching values from this node and all descendants. */
    def collectAll[A](pf: PartialFunction[Operation, A]): Vector[A] =
      val self   = pf.lift(op).toVector
      val nested = op.children.flatMap(_.collectAll(pf))
      self ++ nested

    /** Count all operations in this subtree (including self). */
    def size: Int =
      1 + op.children.map(_.size).sum

    /** Deep transform: recursively apply f bottom-up through the entire subtree via mapChildren. */
    def deepTransform(f: PartialFunction[Operation, Operation]): Operation =
      val withTransformedChildren = op.mapChildren(_.deepTransform(f))
      f.lift(withTransformedChildren).getOrElse(withTransformedChildren)

  extension (module: Module)

    /** Deep transform: apply f to every operation in the tree, bottom-up. Uses mapChildren for generic rebuild. */
    def transform(f: PartialFunction[Operation, Operation]): Module =
      module.copy(topLevel = module.topLevel.map(_.deepTransform(f)))

    /** Transform top-level operations only (no recursion). */
    def transformTopLevel(f: PartialFunction[Operation, Operation]): Module =
      module.copy(topLevel = module.topLevel.map(op => f.lift(op).getOrElse(op)))

    /** Walk all operations in the module depth-first. */
    def walkAll(f: Operation => Unit): Unit =
      module.topLevel.foreach(_.walk(f))
