package io.alnovis.ircraft.emitters.scala

import cats._
import io.alnovis.ircraft.emit.{ BaseEmitter, LanguageSyntax, TypeMapping }

/**
  * Code emitter that produces Scala source files from the semantic IR.
  *
  * Extends [[BaseEmitter]] with Scala-specific syntax rules ([[ScalaSyntax]]) and
  * type mappings ([[ScalaTypeMapping]]). Supports both Scala 2 and Scala 3 targets
  * via [[ScalaEmitterConfig]].
  *
  * Features:
  *  - Native pattern matching via `match`/`case`
  *  - Expression-style function bodies (`def x: Int = expr`)
  *  - Idiomatic Scala naming: `getValue` -> `value`, `snake_case` -> `camelCase`
  *  - Configurable enum style: Scala 3 `enum` or sealed trait hierarchy
  *
  * @param config the emitter configuration controlling Scala version target,
  *               enum style, and `new` keyword usage
  * @tparam F the effect type, constrained to `Monad`
  *
  * @see [[ScalaEmitterConfig]] for configuration options
  * @see [[ScalaSyntax]] for Scala syntax rules
  * @see [[ScalaTypeMapping]] for Scala type name resolution
  * @see [[BaseEmitter]] for the base emitter API
  */
class ScalaEmitter[F[_]: Monad](config: ScalaEmitterConfig = ScalaEmitterConfig()) extends BaseEmitter[F] {
  protected val syntax: LanguageSyntax = new ScalaSyntax(config)
  protected val tm: TypeMapping        = ScalaTypeMapping
}

/**
  * Factory companion for [[ScalaEmitter]], providing convenience constructors
  * for common Scala version targets.
  */
object ScalaEmitter {

  /**
    * Creates a new [[ScalaEmitter]] with the given configuration.
    *
    * @param config the emitter configuration (defaults to Scala 3 with `enum` style)
    * @tparam F the effect type, constrained to `Monad`
    * @return a new Scala emitter
    *
    * @example {{{
    * val emitter = ScalaEmitter[cats.Id](ScalaEmitterConfig(
    *   scalaVersion = ScalaTarget.Scala3,
    *   enumStyle = EnumStyle.Scala3Enum
    * ))
    * }}}
    */
  def apply[F[_]: Monad](config: ScalaEmitterConfig = ScalaEmitterConfig()): ScalaEmitter[F] =
    new ScalaEmitter[F](config)

  /**
    * Creates a [[ScalaEmitter]] targeting Scala 3 with native `enum` syntax.
    *
    * Uses `if ... then ... else` syntax, Scala 3 `enum` declarations,
    * and apply-style construction (no `new` keyword).
    *
    * @tparam F the effect type, constrained to `Monad`
    * @return a Scala 3 emitter
    */
  def scala3[F[_]: Monad]: ScalaEmitter[F] =
    new ScalaEmitter[F](ScalaEmitterConfig(ScalaTarget.Scala3, EnumStyle.Scala3Enum))

  /**
    * Creates a [[ScalaEmitter]] targeting Scala 2 with sealed trait enum encoding.
    *
    * Uses `if (...) ... else ...` syntax, sealed trait + case object enum encoding,
    * and `new` keyword for class instantiation.
    *
    * @tparam F the effect type, constrained to `Monad`
    * @return a Scala 2 emitter
    */
  def scala2[F[_]: Monad]: ScalaEmitter[F] =
    new ScalaEmitter[F](ScalaEmitterConfig(ScalaTarget.Scala2, EnumStyle.SealedTrait, useNewKeyword = true))
}
