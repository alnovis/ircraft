package io.alnovis.ircraft.emitters.scala

/**
  * Configuration for the [[ScalaEmitter]], controlling Scala version-specific behavior.
  *
  * @param scalaVersion the target Scala version (Scala 2 or Scala 3); affects
  *                     syntax choices like `if/then/else` vs `if (...) ... else ...`
  * @param enumStyle    the enum encoding style; `Scala3Enum` uses native `enum`,
  *                     `SealedTrait` uses sealed trait + case object hierarchy
  * @param useNewKeyword whether to emit `new` keyword for class instantiation;
  *                      `true` for Scala 2 style, `false` for Scala 3 / case class style
  *
  * @example {{{
  * // Scala 3 config with native enums and apply-style construction
  * ScalaEmitterConfig(ScalaTarget.Scala3, EnumStyle.Scala3Enum, useNewKeyword = false)
  *
  * // Scala 2 config with sealed traits and new keyword
  * ScalaEmitterConfig(ScalaTarget.Scala2, EnumStyle.SealedTrait, useNewKeyword = true)
  * }}}
  *
  * @see [[ScalaEmitter]] for usage
  * @see [[ScalaTarget]] for available version targets
  * @see [[EnumStyle]] for available enum encoding styles
  */
case class ScalaEmitterConfig(
  scalaVersion: ScalaTarget = ScalaTarget.Scala3,
  enumStyle: EnumStyle = EnumStyle.Scala3Enum,
  useNewKeyword: Boolean = false
)

/**
  * Target Scala version for code emission.
  *
  * Determines version-specific syntax choices in [[ScalaSyntax]].
  */
sealed abstract class ScalaTarget

/**
  * Companion object containing [[ScalaTarget]] variants.
  */
object ScalaTarget {

  /**
    * Target Scala 2.x.
    *
    * Uses `if (...) ... else ...` syntax, `for (...) ...` comprehensions, and
    * `new` keyword for non-case-class instantiation.
    */
  case object Scala2 extends ScalaTarget

  /**
    * Target Scala 3.x.
    *
    * Uses `if ... then ... else ...` syntax, `for ... do` comprehensions, and
    * apply-style construction.
    */
  case object Scala3 extends ScalaTarget
}

/**
  * Enum encoding style for Scala code emission.
  *
  * @see [[ScalaEmitterConfig]] for usage
  */
sealed abstract class EnumStyle

/**
  * Companion object containing [[EnumStyle]] variants.
  */
object EnumStyle {

  /**
    * Use Scala 3 native `enum` syntax.
    *
    * Produces:
    * {{{
    * enum Color:
    *   case Red, Green, Blue
    * }}}
    */
  case object Scala3Enum extends EnumStyle

  /**
    * Use sealed trait + case object hierarchy (Scala 2 compatible).
    *
    * Produces:
    * {{{
    * sealed trait Color
    * object Color {
    *   case object Red extends Color
    *   case object Green extends Color
    *   case object Blue extends Color
    * }
    * }}}
    */
  case object SealedTrait extends EnumStyle
}
