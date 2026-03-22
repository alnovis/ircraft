package io.alnovis.ircraft.core

/**
  * Typed key-value metadata attached to IR operations.
  *
  * Attributes carry compile-time information (field numbers, source locations, modifier sets, etc.) without affecting the
  * structural shape of the IR tree.
  */
sealed trait Attribute:
  def key: String

object Attribute:
  case class StringAttr(key: String, value: String) extends Attribute
  case class IntAttr(key: String, value: Int)       extends Attribute
  case class LongAttr(key: String, value: Long)     extends Attribute
  case class BoolAttr(key: String, value: Boolean)  extends Attribute

  case class StringListAttr(key: String, values: List[String])        extends Attribute
  case class IntListAttr(key: String, values: List[Int])              extends Attribute
  case class AttrListAttr(key: String, values: List[Attribute])       extends Attribute
  case class AttrMapAttr(key: String, values: Map[String, Attribute]) extends Attribute

  /** Cross-reference to another node by its NodeId. */
  case class RefAttr(key: String, target: NodeId) extends Attribute

  given ContentHashable[Attribute] with

    def contentHash(a: Attribute): Int = a match
      case StringAttr(k, v)     => ContentHash.combine(k.hashCode, ContentHash.ofString(v))
      case IntAttr(k, v)        => ContentHash.combine(k.hashCode, v)
      case LongAttr(k, v)       => ContentHash.combine(k.hashCode, ContentHash.ofLong(v))
      case BoolAttr(k, v)       => ContentHash.combine(k.hashCode, ContentHash.ofBoolean(v))
      case StringListAttr(k, v) => ContentHash.combine(k.hashCode, ContentHash.ofList(v))
      case IntListAttr(k, v)    => ContentHash.combine(k.hashCode, ContentHash.ofList(v))
      case AttrListAttr(k, v)   => ContentHash.combine(k.hashCode, ContentHash.ofList(v))
      case AttrMapAttr(k, v) =>
        ContentHash.combine(
          k.hashCode,
          ContentHash.ofMap(v)(using summon[ContentHashable[String]], summon[ContentHashable[Attribute]])
        )
      case RefAttr(k, t) => ContentHash.combine(k.hashCode, t.value)
