package io.alnovis.ircraft.emitters.scala

case class ScalaEmitterConfig(
  scalaVersion: ScalaTarget = ScalaTarget.Scala3,
  enumStyle: EnumStyle = EnumStyle.Scala3Enum,
  useNewKeyword: Boolean = false
)

sealed abstract class ScalaTarget

object ScalaTarget {
  case object Scala2 extends ScalaTarget
  case object Scala3 extends ScalaTarget
}

sealed abstract class EnumStyle

object EnumStyle {
  case object Scala3Enum  extends EnumStyle
  case object SealedTrait extends EnumStyle
}
