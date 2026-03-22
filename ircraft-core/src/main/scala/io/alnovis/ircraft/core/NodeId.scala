package io.alnovis.ircraft.core

/**
  * Content-based identity for IR nodes.
  *
  * Two nodes with the same NodeId are structurally equivalent. This enables:
  *   - Change detection (compare IDs instead of full trees)
  *   - Node caching and reuse between generations
  *   - Incremental processing
  */
opaque type NodeId = Int

object NodeId:
  def apply(hash: Int): NodeId = hash

  def of[A](a: A)(using h: ContentHashable[A]): NodeId = h.contentHash(a)

  extension (id: NodeId)
    def value: Int                      = id
    def matches(other: NodeId): Boolean = id == other

  given ContentHashable[NodeId] with
    def contentHash(a: NodeId): Int = a
