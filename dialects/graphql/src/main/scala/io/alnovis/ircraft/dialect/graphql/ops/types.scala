package io.alnovis.ircraft.dialect.graphql.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.GraphQlDialect

/** GraphQL object type definition. */
case class ObjectTypeOp(
  name: String,
  implementsInterfaces: List[String],
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.ObjectType

  lazy val fields: Vector[GqlFieldOp] = regionOps("fields")

  override def mapChildren(f: Operation => Operation): ObjectTypeOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(implementsInterfaces),
      ContentHash.ofOption(description),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.map(_.estimatedSize).sum

object ObjectTypeOp:

  @targetName("create")
  def apply(
    name: String,
    implementsInterfaces: List[String] = Nil,
    description: Option[String] = None,
    fields: Vector[GqlFieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ObjectTypeOp = new ObjectTypeOp(
    name,
    implementsInterfaces,
    description,
    regions = Vector(Region("fields", fields)),
    attributes,
    span
  )

/** GraphQL input object type definition. */
case class InputObjectTypeOp(
  name: String,
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.InputObjectType

  lazy val fields: Vector[InputFieldOp] = regionOps("fields")

  override def mapChildren(f: Operation => Operation): InputObjectTypeOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofOption(description),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.map(_.estimatedSize).sum

object InputObjectTypeOp:

  @targetName("create")
  def apply(
    name: String,
    description: Option[String] = None,
    fields: Vector[InputFieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): InputObjectTypeOp = new InputObjectTypeOp(
    name,
    description,
    regions = Vector(Region("fields", fields)),
    attributes,
    span
  )

/** GraphQL interface type definition. */
case class InterfaceTypeOp(
  name: String,
  implementsInterfaces: List[String],
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.InterfaceType

  lazy val fields: Vector[GqlFieldOp] = regionOps("fields")

  override def mapChildren(f: Operation => Operation): InterfaceTypeOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(implementsInterfaces),
      ContentHash.ofOption(description),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.map(_.estimatedSize).sum

object InterfaceTypeOp:

  @targetName("create")
  def apply(
    name: String,
    implementsInterfaces: List[String] = Nil,
    description: Option[String] = None,
    fields: Vector[GqlFieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): InterfaceTypeOp = new InterfaceTypeOp(
    name,
    implementsInterfaces,
    description,
    regions = Vector(Region("fields", fields)),
    attributes,
    span
  )

/** GraphQL union type definition (leaf -- members are type name references). */
case class UnionTypeOp(
  name: String,
  memberTypes: List[String],
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = GraphQlDialect.Kinds.UnionType
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(memberTypes),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** GraphQL enum type definition. */
case class EnumTypeOp(
  name: String,
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.EnumType

  lazy val values: Vector[GqlEnumValueOp] = regionOps("values")

  override def mapChildren(f: Operation => Operation): EnumTypeOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofOption(description),
      ContentHash.ofList(values.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + values.map(_.estimatedSize).sum

object EnumTypeOp:

  @targetName("create")
  def apply(
    name: String,
    description: Option[String] = None,
    values: Vector[GqlEnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): EnumTypeOp = new EnumTypeOp(
    name,
    description,
    regions = Vector(Region("values", values)),
    attributes,
    span
  )

/** GraphQL custom scalar type definition (leaf). */
case class ScalarTypeOp(
  name: String,
  specifiedByUrl: Option[String] = None,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = GraphQlDialect.Kinds.ScalarType
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofOption(specifiedByUrl),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** GraphQL directive definition. */
case class DirectiveDefOp(
  name: String,
  locations: List[DirectiveLocation],
  repeatable: Boolean,
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.DirectiveDef

  lazy val arguments: Vector[GqlArgumentOp] = regionOps("arguments")

  override def mapChildren(f: Operation => Operation): DirectiveDefOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(locations)(using summon[ContentHashable[DirectiveLocation]]),
      ContentHash.ofBoolean(repeatable),
      ContentHash.ofOption(description),
      ContentHash.ofList(arguments.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + arguments.map(_.estimatedSize).sum

object DirectiveDefOp:

  @targetName("create")
  def apply(
    name: String,
    locations: List[DirectiveLocation] = Nil,
    repeatable: Boolean = false,
    description: Option[String] = None,
    arguments: Vector[GqlArgumentOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): DirectiveDefOp = new DirectiveDefOp(
    name,
    locations,
    repeatable,
    description,
    regions = Vector(Region("arguments", arguments)),
    attributes,
    span
  )
