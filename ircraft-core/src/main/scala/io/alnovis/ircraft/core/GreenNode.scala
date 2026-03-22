package io.alnovis.ircraft.core

/** Base trait for all immutable, content-addressable IR nodes.
  *
  * Green nodes form the "Green" part of Red-Green trees (Roslyn):
  *   - Immutable (implemented as Scala 3 case classes)
  *   - Content-addressable (identity based on content hash)
  *   - No parent references (navigation is top-down only)
  *   - Relative sizes (width), not absolute positions
  *
  * @see
  *   [[https://ericlippert.com/2012/06/08/red-green-trees/ Eric Lippert: Red-Green Trees]]
  */
trait GreenNode:

  /** Content hash for structural identity. Two nodes with equal contentHash are structurally equivalent. */
  def contentHash: Int

  /** Width of this node in the source representation. Used for computing absolute positions in Red Tree. */
  def width: Int
