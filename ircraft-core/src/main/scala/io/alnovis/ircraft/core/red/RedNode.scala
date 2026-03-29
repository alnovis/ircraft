package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.Operation

/**
  * Red Tree node: wraps an immutable Green node with parent pointer and absolute position.
  *
  * Red nodes are ephemeral views -- thrown away on any Green tree change. Children are lazy (computed on first access),
  * so only navigated branches materialize.
  *
  * @param green
  *   the wrapped immutable Green operation
  * @param parent
  *   parent Red node (None for root)
  * @param offset
  *   absolute position in the tree
  * @param childIndex
  *   index among parent's children
  * @see
  *   [[https://ericlippert.com/2012/06/08/red-green-trees/ Eric Lippert: Red-Green Trees]]
  */
final class RedNode[+T <: Operation](
  val green: T,
  val parent: Option[RedNode[Operation]],
  val offset: Int,
  val childIndex: Int
):

  /** All children as RedNodes, lazily constructed from green.children. */
  lazy val children: Vector[RedNode[Operation]] =
    val greenChildren = green.children
    greenChildren.zipWithIndex
      .foldLeft((Vector.empty[RedNode[Operation]], offset + 1)) {
        case ((acc, nextOffset), (child, idx)) =>
          val node = RedNode(child, Some(this.asInstanceOf[RedNode[Operation]]), nextOffset, idx)
          (acc :+ node, nextOffset + nodeSize(child))
      }
      ._1

  /** Depth in the tree (root = 0). */
  def depth: Int = parent match
    case None    => 0
    case Some(p) => p.depth + 1

  /** Path from root to this node (root first). */
  def ancestors: List[RedNode[Operation]] =
    def go(node: RedNode[Operation], acc: List[RedNode[Operation]]): List[RedNode[Operation]] =
      node.parent match
        case None    => node :: acc
        case Some(p) => go(p, node :: acc)
    go(this.asInstanceOf[RedNode[Operation]], Nil)

  /** Navigate to next sibling. */
  def nextSibling: Option[RedNode[Operation]] =
    parent.flatMap { p =>
      val next = childIndex + 1
      if next < p.children.size then Some(p.children(next))
      else None
    }

  /** Navigate to previous sibling. */
  def prevSibling: Option[RedNode[Operation]] =
    parent.flatMap { p =>
      if childIndex > 0 then Some(p.children(childIndex - 1))
      else None
    }

  /** Find first descendant matching predicate (depth-first). */
  def find(p: Operation => Boolean): Option[RedNode[Operation]] =
    if p(green) then Some(this.asInstanceOf[RedNode[Operation]])
    else children.view.flatMap(_.find(p)).headOption

  /** Collect all descendants matching predicate. */
  def collect(p: Operation => Boolean): Vector[RedNode[Operation]] =
    val self = if p(green) then Vector(this.asInstanceOf[RedNode[Operation]]) else Vector.empty
    self ++ children.flatMap(_.collect(p))

  /** Content hash delegates to green node. */
  def contentHash: Int = green.contentHash

  /** End offset (exclusive). */
  def endOffset: Int = offset + nodeSize(green)

  override def toString: String =
    s"RedNode(${green.kind.qualifiedName}, offset=$offset, depth=$depth)"

private[red] def nodeSize(op: Operation): Int =
  1 + op.children.map(nodeSize).sum
