package io.alnovis.ircraft.dialect.semantic.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect
import io.alnovis.ircraft.dialect.semantic.expr.Block

/** Method declaration. */
case class MethodOp(
  name: String,
  returnType: TypeRef,
  parameters: List[Parameter] = Nil,
  modifiers: Set[Modifier] = Set(Modifier.Public),
  typeParams: List[TypeParam] = Nil,
  body: Option[Block] = None,
  javadoc: Option[String] = None,
  annotations: List[String] = Nil,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.Method
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(returnType),
      ContentHash.ofList(parameters)(using Parameter.given_ContentHashable_Parameter),
      ContentHash.ofSet(modifiers),
      ContentHash.ofOption(body)(using io.alnovis.ircraft.dialect.semantic.expr.Block.given_ContentHashable_Block)
    )

  val estimatedSize: Int = 1

  def isAbstract: Boolean = modifiers.contains(Modifier.Abstract) && body.isEmpty
  def isDefault: Boolean  = modifiers.contains(Modifier.Default) && body.isDefined
  def isStatic: Boolean   = modifiers.contains(Modifier.Static)
