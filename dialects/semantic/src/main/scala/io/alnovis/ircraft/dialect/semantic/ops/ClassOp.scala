package io.alnovis.ircraft.dialect.semantic.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect
import io.alnovis.ircraft.dialect.semantic.expr.Block

/** Class declaration. */
case class ClassOp(
  name: String,
  modifiers: Set[Modifier],
  typeParams: List[TypeParam],
  superClass: Option[TypeRef],
  implementsTypes: List[TypeRef],
  regions: Vector[Region],
  javadoc: Option[String],
  annotations: List[String],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.Class

  lazy val fields: Vector[FieldDeclOp]         = regionOps("fields")
  lazy val constructors: Vector[ConstructorOp] = regionOps("constructors")
  lazy val methods: Vector[MethodOp]           = regionOps("methods")
  lazy val nestedTypes: Vector[Operation]      = regionOps("nestedTypes")

  override def mapChildren(f: Operation => Operation): ClassOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(modifiers),
      ContentHash.ofOption(superClass),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable),
      ContentHash.ofList(constructors.toList)(using Operation.operationHashable),
      ContentHash.ofList(methods.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedTypes.toList)(using Operation.operationHashable)
    )

  lazy val width: Int =
    1 + fields.map(_.width).sum + methods.map(_.width).sum + nestedTypes.map(_.width).sum

  def isAbstract: Boolean = modifiers.contains(Modifier.Abstract)

object ClassOp:

  @targetName("create")
  def apply(
    name: String,
    modifiers: Set[Modifier] = Set(Modifier.Public),
    typeParams: List[TypeParam] = Nil,
    superClass: Option[TypeRef] = None,
    implementsTypes: List[TypeRef] = Nil,
    fields: Vector[FieldDeclOp] = Vector.empty,
    constructors: Vector[ConstructorOp] = Vector.empty,
    methods: Vector[MethodOp] = Vector.empty,
    nestedTypes: Vector[Operation] = Vector.empty,
    javadoc: Option[String] = None,
    annotations: List[String] = Nil,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ClassOp = new ClassOp(
    name,
    modifiers,
    typeParams,
    superClass,
    implementsTypes,
    regions = Vector(
      Region("fields", fields),
      Region("constructors", constructors),
      Region("methods", methods),
      Region("nestedTypes", nestedTypes)
    ),
    javadoc,
    annotations,
    attributes,
    span
  )

/** Field declaration. */
case class FieldDeclOp(
  name: String,
  fieldType: TypeRef,
  modifiers: Set[Modifier] = Set.empty,
  defaultValue: Option[String] = None,
  javadoc: Option[String] = None,
  annotations: List[String] = Nil,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.FieldDecl
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(fieldType),
      ContentHash.ofSet(modifiers)
    )

  val width: Int = 1

/** Constructor declaration. */
case class ConstructorOp(
  parameters: List[Parameter],
  modifiers: Set[Modifier] = Set(Modifier.Public),
  body: Option[Block] = None,
  annotations: List[String] = Nil,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.Constructor
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofList(parameters)(using Parameter.given_ContentHashable_Parameter),
      ContentHash.ofSet(modifiers),
      ContentHash.ofOption(body)(using Block.given_ContentHashable_Block)
    )

  val width: Int = 1
