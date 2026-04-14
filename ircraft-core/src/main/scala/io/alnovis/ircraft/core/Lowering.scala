package io.alnovis.ircraft.core

import cats._
import cats.data._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Module, SemanticF }

// Lowering type alias defined in package.scala

/**
  * Factory methods for creating [[Lowering]] instances.
  *
  * A `Lowering[F, Source]` is a `Kleisli[F, Source, Module[Fix[SemanticF]]]` --
  * an effectful function that converts an external source representation (e.g.,
  * a protobuf descriptor, an OpenAPI spec, a parsed AST) into the ircraft
  * semantic IR ([[Module]] of `Fix[SemanticF]`).
  *
  * Lowering is the entry point of the ircraft pipeline: it produces the initial
  * IR that subsequent [[Pass]] transformations operate on.
  *
  * {{{
  * import cats.Id
  *
  * case class MySource(name: String)
  *
  * val lowering: Lowering[Id, MySource] = Lowering.pure[Id, MySource] { src =>
  *   Module(src.name, Vector.empty)
  * }
  * }}}
  *
  * @see [[Pass]] for transformations applied after lowering
  * @see [[Pipeline]] for composing passes into a full pipeline
  */
object Lowering {

  /**
    * Creates a pure (non-effectful) lowering from the given function.
    *
    * The function `f` is lifted into the effect `F` via `Applicative.pure`.
    *
    * @tparam F      the effect type (must have an [[cats.Applicative]] instance)
    * @tparam Source the external source type to convert from
    * @param f the pure conversion function from `Source` to a semantic module
    * @return a new `Lowering[F, Source]` wrapping the conversion
    */
  def pure[F[_]: Applicative, Source](f: Source => Module[Fix[SemanticF]]): Lowering[F, Source] =
    Kleisli(source => Applicative[F].pure(f(source)))

  /**
    * Creates an effectful lowering from the given function.
    *
    * @tparam F      the effect type
    * @tparam Source the external source type to convert from
    * @param f the effectful conversion function from `Source` to `F[Module[Fix[SemanticF]]]`
    * @return a new `Lowering[F, Source]` wrapping the conversion
    */
  def apply[F[_], Source](f: Source => F[Module[Fix[SemanticF]]]): Lowering[F, Source] =
    Kleisli(f)
}
