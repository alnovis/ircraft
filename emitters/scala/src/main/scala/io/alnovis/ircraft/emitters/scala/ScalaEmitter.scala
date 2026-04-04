package io.alnovis.ircraft.emitters.scala

import cats.*
import io.alnovis.ircraft.emit.{ BaseEmitter, LanguageSyntax, TypeMapping }

class ScalaEmitter[F[_]: Monad](config: ScalaEmitterConfig = ScalaEmitterConfig()) extends BaseEmitter[F]:
  protected val syntax: LanguageSyntax = ScalaSyntax(config)
  protected val tm: TypeMapping        = ScalaTypeMapping

object ScalaEmitter:

  def apply[F[_]: Monad](config: ScalaEmitterConfig = ScalaEmitterConfig()): ScalaEmitter[F] =
    new ScalaEmitter[F](config)

  def scala3[F[_]: Monad]: ScalaEmitter[F] =
    new ScalaEmitter[F](ScalaEmitterConfig(ScalaTarget.Scala3, EnumStyle.Scala3Enum))

  def scala2[F[_]: Monad]: ScalaEmitter[F] =
    new ScalaEmitter[F](ScalaEmitterConfig(ScalaTarget.Scala2, EnumStyle.SealedTrait, useNewKeyword = true))
