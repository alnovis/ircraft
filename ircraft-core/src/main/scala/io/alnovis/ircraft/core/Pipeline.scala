package io.alnovis.ircraft.core

import cats._
import cats.data._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Module, SemanticF }

/**
  * Combinators for composing and executing [[Pass]] pipelines.
  *
  * A pipeline is simply a [[Pass]] built by sequentially composing multiple passes
  * via Kleisli `andThen`. This object provides convenience methods for constructing
  * pipelines from varargs, conditionally-enabled pass lists, and for running
  * a pipeline against a module.
  *
  * {{{
  * import cats.Id
  *
  * val p1: Pass[Id] = Pass.pure[Id]("step1")(identity)
  * val p2: Pass[Id] = Pass.pure[Id]("step2")(identity)
  * val pipeline: Pass[Id] = Pipeline.of[Id](p1, p2)
  * }}}
  *
  * @see [[Pass]] for creating individual passes
  * @see [[Lowering]] for producing initial modules to feed into a pipeline
  */
object Pipeline {

  /**
    * Composes one or more passes into a sequential pipeline.
    *
    * Passes are composed left-to-right using Kleisli `andThen`, so the output
    * of each pass is fed as input to the next.
    *
    * @tparam F the effect type (must have a [[cats.FlatMap]] instance)
    * @param first the first pass to apply
    * @param rest  additional passes to apply in order
    * @return a single `Pass[F]` representing the sequential composition
    */
  def of[F[_]: FlatMap](first: Pass[F], rest: Pass[F]*): Pass[F] =
    rest.foldLeft(first)(_ andThen _)

  /**
    * Builds a pipeline from a vector of passes, each paired with an enabled flag.
    *
    * Only passes whose boolean flag is `true` are included in the resulting pipeline.
    * If no passes are enabled, the result is [[Pass.id]] (the identity pass).
    *
    * {{{
    * val passes: Vector[(Pass[Id], Boolean)] = Vector(
    *   (renamePass, true),
    *   (debugPass, false),
    *   (optimizePass, true)
    * )
    * val pipeline: Pass[Id] = Pipeline.build[Id](passes)
    * // Only renamePass and optimizePass are included
    * }}}
    *
    * @tparam F the effect type (must have a [[cats.Monad]] instance)
    * @param passes a vector of `(pass, enabled)` pairs
    * @return a single `Pass[F]` composing all enabled passes, or identity if none are enabled
    */
  def build[F[_]: Monad](passes: Vector[(Pass[F], Boolean)]): Pass[F] = {
    val enabled = passes.collect { case (p, true) => p }
    if (enabled.isEmpty) Pass.id[F]
    else enabled.reduceLeft(_ andThen _)
  }

  /**
    * Executes a pipeline against a module, producing the transformed module inside `F`.
    *
    * This is a convenience method equivalent to `pipeline.run(module)`.
    *
    * @tparam F the effect type
    * @param pipeline the composed pass pipeline to execute
    * @param module   the input module to transform
    * @return the transformed module wrapped in `F`
    */
  def run[F[_]](pipeline: Pass[F], module: Module[Fix[SemanticF]]): F[Module[Fix[SemanticF]]] =
    pipeline.run(module)
}
