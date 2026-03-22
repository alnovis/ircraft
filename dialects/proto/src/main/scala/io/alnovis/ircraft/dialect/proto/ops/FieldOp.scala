package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect
import io.alnovis.ircraft.dialect.proto.types.ConflictType

/** Protobuf field declaration. Maps to MergedField.
  *
  * @param name
  *   proto field name (snake_case)
  * @param javaName
  *   java-style camelCase name
  * @param number
  *   field number
  * @param fieldType
  *   resolved type reference
  * @param conflictType
  *   type conflict across versions
  * @param presentInVersions
  *   versions where this field exists
  * @param isOptional
  *   true if field is optional in any version
  * @param isRepeated
  *   true if field is repeated in any version
  * @param isMap
  *   true if field is a map
  * @param typesPerVersion
  *   the original proto type string per version (for conflict detection)
  */
case class FieldOp(
    name: String,
    javaName: String,
    number: Int,
    fieldType: TypeRef,
    conflictType: ConflictType = ConflictType.None,
    presentInVersions: Set[String] = Set.empty,
    isOptional: Boolean = false,
    isRepeated: Boolean = false,
    isMap: Boolean = false,
    typesPerVersion: Map[String, String] = Map.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Field

  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      number,
      summon[ContentHashable[TypeRef]].contentHash(fieldType),
      conflictType.ordinal,
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofBoolean(isOptional),
      ContentHash.ofBoolean(isRepeated),
      ContentHash.ofBoolean(isMap),
    )

  val width: Int = 1

  def hasConflict: Boolean = conflictType != ConflictType.None
