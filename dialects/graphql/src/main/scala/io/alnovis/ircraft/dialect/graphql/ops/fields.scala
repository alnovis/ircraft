package io.alnovis.ircraft.dialect.graphql.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.GraphQlDialect

/** GraphQL field definition (on object or interface types). */
case class GqlFieldOp(
  name: String,
  fieldType: TypeRef,
  description: Option[String],
  isDeprecated: Boolean,
  deprecationReason: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.GqlField

  lazy val arguments: Vector[GqlArgumentOp] = regionOps("arguments")

  override def mapChildren(f: Operation => Operation): GqlFieldOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(fieldType),
      ContentHash.ofOption(description),
      ContentHash.ofBoolean(isDeprecated),
      ContentHash.ofOption(deprecationReason),
      ContentHash.ofList(arguments.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + arguments.map(_.estimatedSize).sum

object GqlFieldOp:

  @targetName("create")
  def apply(
    name: String,
    fieldType: TypeRef,
    description: Option[String] = None,
    isDeprecated: Boolean = false,
    deprecationReason: Option[String] = None,
    arguments: Vector[GqlArgumentOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): GqlFieldOp = new GqlFieldOp(
    name,
    fieldType,
    description,
    isDeprecated,
    deprecationReason,
    regions = Vector(Region("arguments", arguments)),
    attributes,
    span
  )

/** GraphQL input field definition (leaf). */
case class InputFieldOp(
  name: String,
  fieldType: TypeRef,
  defaultValue: Option[String] = None,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = GraphQlDialect.Kinds.InputField
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(fieldType),
      ContentHash.ofOption(defaultValue),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** GraphQL argument definition (leaf). */
case class GqlArgumentOp(
  name: String,
  argType: TypeRef,
  defaultValue: Option[String] = None,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = GraphQlDialect.Kinds.GqlArgument
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(argType),
      ContentHash.ofOption(defaultValue),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** GraphQL enum value definition (leaf). */
case class GqlEnumValueOp(
  name: String,
  description: Option[String] = None,
  isDeprecated: Boolean = false,
  deprecationReason: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = GraphQlDialect.Kinds.GqlEnumValue
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofOption(description),
      ContentHash.ofBoolean(isDeprecated),
      ContentHash.ofOption(deprecationReason)
    )

  val estimatedSize: Int = 1
