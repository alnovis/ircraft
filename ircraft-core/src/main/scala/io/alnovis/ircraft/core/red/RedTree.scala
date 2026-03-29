package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.{ Module, Operation }

/**
  * Facade for building and querying Red Trees.
  */
object RedTree:

  /** Build a Red Tree from a Module. Root has no parent, offset 0. */
  def from(module: Module): RedNode[Module] =
    RedNode(module, parent = None, offset = 0, childIndex = 0)

  /** Find the deepest node containing the given absolute position. */
  def locate(root: RedNode[Module], position: Int): Option[RedNode[Operation]] =
    locateIn(root.asInstanceOf[RedNode[Operation]], position)

  /** Collect all nodes matching a predicate. */
  def collectWithContext(
    root: RedNode[Module],
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
