package io.alnovis.ircraft.core

/** Identifies the "shape" of an operation within a dialect.
  *
  * @param dialect
  *   the dialect namespace (e.g., "proto", "semantic", "java.code")
  * @param name
  *   the operation name within the dialect (e.g., "message", "class", "method")
  */
case class NodeKind(dialect: String, name: String):
  def qualifiedName: String = s"$dialect.$name"

  override def toString: String = qualifiedName

object NodeKind:
  given ContentHashable[NodeKind] with
    def contentHash(a: NodeKind): Int =
      ContentHash.combine(ContentHash.ofString(a.dialect), ContentHash.ofString(a.name))
