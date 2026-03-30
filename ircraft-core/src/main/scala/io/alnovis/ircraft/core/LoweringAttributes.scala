package io.alnovis.ircraft.core

/**
  * Generic attributes for tracking provenance during IR lowering (A -> X).
  *
  * When a lowering pass converts a source dialect operation to a target dialect operation, the source NodeKind is lost.
  * These attributes preserve that information so downstream passes can distinguish origins.
  *
  * {{{
  * // In a lowering pass:
  * val enumClass = EnumClassOp(
  *   name = source.enumName,
  *   attributes = LoweringAttributes.sourceKindAttr(source)
  * )
  * // enumClass now has ir.sourceNodeKind = "proto:conflict_enum"
  * }}}
  *
  * @see docs/EMIT_BASED_LOWERING.md for future automatic provenance via emit-based framework
  */
object LoweringAttributes:

  /** Attribute key: original NodeKind of the source operation before lowering. */
  val SourceNodeKind: String = "ir.sourceNodeKind"

  /**
    * Per-version source type names (version -> fully qualified type name).
    *
    * Set by the client (bridge) on source dialect operations before lowering.
    * Lowering passes read this to resolve the concrete source type for each
    * version's implementation class.
    *
    * Stored as `AttrMapAttr` where keys are version identifiers and values
    * are `StringAttr` with fully qualified class names.
    *
    * {{{
    * // Client sets on MessageOp before pipeline:
    * val attrs = AttributeMap(Attribute.AttrMapAttr(SourceTypeNames, Map(
    *   "v1" -> Attribute.StringAttr("v1", "com.example.proto.v1.Common.Money"),
    *   "v2" -> Attribute.StringAttr("v2", "com.example.proto.v2.Common.Money")
    * )))
    *
    * // Lowering pass reads:
    * val typeName = resolveSourceTypeName(msg, "v1")
    * // -> "com.example.proto.v1.Common.Money"
    * }}}
    */
  val SourceTypeNames: String = "source.typeNames"

  /** Create an AttributeMap with the source operation's NodeKind. */
  def sourceKindAttr(source: Operation): AttributeMap =
    AttributeMap(Attribute.StringAttr(SourceNodeKind, source.kind.toString))
