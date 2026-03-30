package io.alnovis.ircraft.dialect.openapi.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect

/** OpenAPI schema object -- represents a named object type with properties. */
case class SchemaObjectOp(
  name: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.SchemaObject

  lazy val properties: Vector[SchemaPropertyOp] = regionOps("properties")

  override def mapChildren(f: Operation => Operation): SchemaObjectOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(properties.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + properties.map(_.estimatedSize).sum

object SchemaObjectOp:

  @targetName("create")
  def apply(
    name: String,
    properties: Vector[SchemaPropertyOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): SchemaObjectOp = new SchemaObjectOp(
    name,
    regions = Vector(Region("properties", properties)),
    attributes,
    span
  )

/** OpenAPI schema property (leaf). */
case class SchemaPropertyOp(
  name: String,
  fieldType: TypeRef,
  required: Boolean,
  description: Option[String] = None,
  defaultValue: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.SchemaProperty
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(fieldType),
      ContentHash.ofBoolean(required),
      ContentHash.ofOption(description),
      ContentHash.ofOption(defaultValue)
    )

  val estimatedSize: Int = 1

/** OpenAPI schema enum -- named enum with a base type and values. */
case class SchemaEnumOp(
  name: String,
  baseType: TypeRef,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.SchemaEnum

  lazy val values: Vector[SchemaEnumValueOp] = regionOps("values")

  override def mapChildren(f: Operation => Operation): SchemaEnumOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(baseType),
      ContentHash.ofList(values.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + values.map(_.estimatedSize).sum

object SchemaEnumOp:

  @targetName("create")
  def apply(
    name: String,
    baseType: TypeRef,
    values: Vector[SchemaEnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): SchemaEnumOp = new SchemaEnumOp(
    name,
    baseType,
    regions = Vector(Region("values", values)),
    attributes,
    span
  )

/** OpenAPI schema enum value (leaf). */
case class SchemaEnumValueOp(
  name: String,
  value: String,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.SchemaEnumValue
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofString(value)
    )

  val estimatedSize: Int = 1

/** OpenAPI schema composition (oneOf/anyOf/allOf). */
case class SchemaCompositionOp(
  compositionKind: CompositionKind,
  discriminatorProperty: Option[String],
  discriminatorMapping: Map[String, String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.SchemaComposition

  lazy val schemas: Vector[Operation] = regionOps("schemas")

  override def mapChildren(f: Operation => Operation): SchemaCompositionOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      summon[ContentHashable[CompositionKind]].contentHash(compositionKind),
      ContentHash.ofOption(discriminatorProperty),
      ContentHash.ofList(discriminatorMapping.toList.sorted.map((k, v) =>
        ContentHash.combine(ContentHash.ofString(k), ContentHash.ofString(v))
      )),
      ContentHash.ofList(schemas.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + schemas.map(_.estimatedSize).sum

object SchemaCompositionOp:

  @targetName("create")
  def apply(
    compositionKind: CompositionKind,
    discriminatorProperty: Option[String] = None,
    discriminatorMapping: Map[String, String] = Map.empty,
    schemas: Vector[Operation] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): SchemaCompositionOp = new SchemaCompositionOp(
    compositionKind,
    discriminatorProperty,
    discriminatorMapping,
    regions = Vector(Region("schemas", schemas)),
    attributes,
    span
  )

/** OpenAPI array schema (leaf). */
case class SchemaArrayOp(
  itemType: TypeRef,
  minItems: Option[Int] = None,
  maxItems: Option[Int] = None,
  uniqueItems: Boolean = false,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.SchemaArray
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      summon[ContentHashable[TypeRef]].contentHash(itemType),
      ContentHash.ofOption(minItems),
      ContentHash.ofOption(maxItems),
      ContentHash.ofBoolean(uniqueItems)
    )

  val estimatedSize: Int = 1

/** OpenAPI discriminator (leaf). */
case class DiscriminatorOp(
  propertyName: String,
  mapping: Map[String, String] = Map.empty,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Discriminator
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(propertyName),
      ContentHash.ofList(mapping.toList.sorted.map((k, v) =>
        ContentHash.combine(ContentHash.ofString(k), ContentHash.ofString(v))
      ))
    )

  val estimatedSize: Int = 1
