package io.alnovis.ircraft.dialect.semantic.ops

import io.alnovis.ircraft.core.*

/** Method/constructor parameter. */
case class Parameter(
  name: String,
  paramType: TypeRef,
  modifiers: Set[Modifier] = Set.empty,
  annotations: List[String] = Nil
)

object Parameter:

  given ContentHashable[Parameter] with

    def contentHash(a: Parameter): Int =
      ContentHash.combine(
        ContentHash.ofString(a.name),
        summon[ContentHashable[TypeRef]].contentHash(a.paramType),
        ContentHash.ofSet(a.modifiers),
        ContentHash.ofList(a.annotations)
      )

/** Generic type parameter. */
case class TypeParam(
  name: String,
  upperBounds: List[TypeRef] = Nil,
  lowerBounds: List[TypeRef] = Nil
)

object TypeParam:

  given ContentHashable[TypeParam] with

    def contentHash(a: TypeParam): Int =
      val typeRefHash = summon[ContentHashable[TypeRef]]
      ContentHash.combine(
        ContentHash.ofString(a.name),
        ContentHash.ofList(a.upperBounds)(using typeRefHash),
        ContentHash.ofList(a.lowerBounds)(using typeRefHash)
      )
