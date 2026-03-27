package io.alnovis.ircraft.dialect.semantic.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect
import io.alnovis.ircraft.dialect.semantic.expr.Expression

/** Enum class declaration. */
case class EnumClassOp(
  name: String,
  modifiers: Set[Modifier],
  implementsTypes: List[TypeRef],
  regions: Vector[Region],
  javadoc: Option[String],
  annotations: List[String],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.EnumClass

  lazy val constants: Vector[EnumConstantOp]   = regionOps("constants")
  lazy val fields: Vector[FieldDeclOp]         = regionOps("fields")
  lazy val constructors: Vector[ConstructorOp] = regionOps("constructors")
  lazy val methods: Vector[MethodOp]           = regionOps("methods")

  override def mapChildren(f: Operation => Operation): EnumClassOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(modifiers),
      ContentHash.ofList(implementsTypes)(using summon[ContentHashable[TypeRef]]),
      ContentHash.ofOption(javadoc),
      ContentHash.ofList(annotations),
      ContentHash.ofList(constants.toList)(using Operation.operationHashable),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable),
      ContentHash.ofList(constructors.toList)(using Operation.operationHashable),
      ContentHash.ofList(methods.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + constants.size + methods.map(_.estimatedSize).sum

object EnumClassOp:

  @targetName("create")
  def apply(
    name: String,
    modifiers: Set[Modifier] = Set(Modifier.Public),
    implementsTypes: List[TypeRef] = Nil,
    constants: Vector[EnumConstantOp] = Vector.empty,
    fields: Vector[FieldDeclOp] = Vector.empty,
    constructors: Vector[ConstructorOp] = Vector.empty,
    methods: Vector[MethodOp] = Vector.empty,
    javadoc: Option[String] = None,
    annotations: List[String] = Nil,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): EnumClassOp = new EnumClassOp(
    name,
    modifiers,
    implementsTypes,
    regions = Vector(
      Region("constants", constants),
      Region("fields", fields),
      Region("constructors", constructors),
      Region("methods", methods)
    ),
    javadoc,
    annotations,
    attributes,
    span
  )

/** Enum constant (e.g., OK(0), ERROR(1)). */
case class EnumConstantOp(
  name: String,
  arguments: List[Expression] = Nil,
  javadoc: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.EnumConstant
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(arguments)(using summon[ContentHashable[Expression]]),
      ContentHash.ofOption(javadoc)
    )

  val estimatedSize: Int = 1
