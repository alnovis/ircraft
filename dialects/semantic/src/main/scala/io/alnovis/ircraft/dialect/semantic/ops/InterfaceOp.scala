package io.alnovis.ircraft.dialect.semantic.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect

/** Interface declaration. */
case class InterfaceOp(
  name: String,
  modifiers: Set[Modifier],
  typeParams: List[TypeParam],
  extendsTypes: List[TypeRef],
  regions: Vector[Region],
  javadoc: Option[String],
  annotations: List[String],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.Interface

  lazy val methods: Vector[MethodOp]      = regionOps("methods")
  lazy val nestedTypes: Vector[Operation] = regionOps("nestedTypes")

  override def mapChildren(f: Operation => Operation): InterfaceOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(modifiers),
      ContentHash.ofList(methods.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedTypes.toList)(using Operation.operationHashable)
    )

  lazy val width: Int = 1 + methods.map(_.width).sum + nestedTypes.map(_.width).sum

object InterfaceOp:

  @targetName("create")
  def apply(
    name: String,
    modifiers: Set[Modifier] = Set(Modifier.Public),
    typeParams: List[TypeParam] = Nil,
    extendsTypes: List[TypeRef] = Nil,
    methods: Vector[MethodOp] = Vector.empty,
    nestedTypes: Vector[Operation] = Vector.empty,
    javadoc: Option[String] = None,
    annotations: List[String] = Nil,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): InterfaceOp = new InterfaceOp(
    name,
    modifiers,
    typeParams,
    extendsTypes,
    regions = Vector(Region("methods", methods), Region("nestedTypes", nestedTypes)),
    javadoc,
    annotations,
    attributes,
    span
  )
