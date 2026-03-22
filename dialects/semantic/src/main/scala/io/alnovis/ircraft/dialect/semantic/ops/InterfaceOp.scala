package io.alnovis.ircraft.dialect.semantic.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect

/** Interface declaration. */
case class InterfaceOp(
    name: String,
    modifiers: Set[Modifier] = Set(Modifier.Public),
    typeParams: List[TypeParam] = Nil,
    extendsTypes: List[TypeRef] = Nil,
    methods: Vector[MethodOp] = Vector.empty,
    nestedTypes: Vector[Operation] = Vector.empty,
    javadoc: Option[String] = None,
    annotations: List[String] = Nil,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.Interface

  val regions: Vector[Region] = Vector(
    Region("methods", methods),
    Region("nestedTypes", nestedTypes),
  )

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(modifiers),
      ContentHash.ofList(methods.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedTypes.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + methods.map(_.width).sum + nestedTypes.map(_.width).sum
