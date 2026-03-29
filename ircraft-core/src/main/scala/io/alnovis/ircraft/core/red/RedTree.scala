package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.{ IrModule, Operation }

/**
  * Facade for building and querying Red Trees.
  */
object RedTree:

  /** Build a Red Tree from a IrModule. Root has no parent, offset 0. */
  def from(module: IrModule): RedNode[IrModule] =
    RedNode(module, parent = None, offset = 0, childIndex = 0)

  /** Find the deepest node containing the given absolute position. */
  def locate(root: RedNode[IrModule], position: Int): Option[RedNode[Operation]] =
    locateIn(root.asInstanceOf[RedNode[Operation]], position)

  /** Collect all nodes matching a predicate. */
  def collectWithContext(
    root: RedNode[IrModule],
    p: Operation => Boolean
  ): Vector[RedNode[Operation]] =
    root.collect(p)

  private def locateIn(node: RedNode[Operation], position: Int): Option[RedNode[Operation]] =
    if position < node.offset || position >= node.endOffset then None
    else
      node.children.view
        .filter(c => position >= c.offset && position < c.endOffset)
        .flatMap(c => locateIn(c, position))
        .headOption
        .orElse(Some(node))
