package io.alnovis.ircraft.emitters.scala

case class ScalaEmitterConfig(
  scalaVersion: ScalaTarget = ScalaTarget.Scala3,
  enumStyle: EnumStyle = EnumStyle.Scala3Enum,
  useNewKeyword: Boolean = false,
)

enum ScalaTarget:
  case Scala2
  case Scala3

enum EnumStyle:
  case Scala3Enum
  case SealedTrait
