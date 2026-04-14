package io.alnovis.ircraft.core

import cats._
import cats.data._
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Meta, Module, SemanticF }

// Pass type alias defined in package.scala: Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]

/**
  * Factory methods for creating [[Pass]] instances.
  *
  * A `Pass[F]` is a `Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]` --
  * an effectful transformation from a semantic module to a semantic module. Passes
  * are the fundamental unit of composition in an ircraft pipeline.
  *
  * Each factory method automatically appends the pass name to a trace chain stored
  * in the module's [[Meta]], accessible via [[Pass.traceKey]]. This trace provides
  * an audit trail of all passes that have been applied to a module.
  *
  * {{{
  * import cats.Id
  *
  * val renamePass: Pass[Id] = Pass.pure[Id]("rename") { module =>
  *   module.copy(name = module.name.toUpperCase)
  * }
  * }}}
  *
  * @see [[Pipeline]] for composing multiple passes into a pipeline
  * @see [[Lowering]] for creating an initial module from an external source
  */
object Pass {

  /**
    * Metadata key for the pass execution trace.
    *
    * The trace is a `Chain[String]` of pass names in the order they were applied.
    * Each pass factory method appends the pass name to this chain automatically.
    */
  val traceKey: Meta.Key[Chain[String]] = Meta.Key("pass.trace")

  /**
    * Creates an effectful pass with the given name.
    *
    * The function `f` receives a module and returns a transformed module inside
    * the effect `F`. The pass name is automatically appended to the module's
    * trace metadata after `f` completes.
    *
    * @tparam F the effect type (must have a [[cats.Functor]] instance)
    * @param name a human-readable name for this pass (used in trace metadata)
    * @param f    the effectful transformation function
    * @return a new `Pass[F]` wrapping the transformation
    */
  def apply[F[_]: Functor](name: String)(f: Module[Fix[SemanticF]] => F[Module[Fix[SemanticF]]]): Pass[F] =
    Kleisli { m =>
      f(m).map(appendTrace(_, name))
    }

  /**
    * Creates a pure (non-effectful) pass with the given name.
    *
    * The function `f` is a plain transformation that is lifted into the effect `F`
    * via `Applicative.pure`. The pass name is appended to the module's trace
    * metadata automatically.
    *
    * @tparam F the effect type (must have an [[cats.Applicative]] instance)
    * @param name a human-readable name for this pass (used in trace metadata)
    * @param f    the pure transformation function
    * @return a new `Pass[F]` wrapping the transformation
    */
  def pure[F[_]: Applicative](name: String)(f: Module[Fix[SemanticF]] => Module[Fix[SemanticF]]): Pass[F] =
    Kleisli { m =>
      Applicative[F].pure(appendTrace(f(m), name))
    }

  /**
    * Creates an identity pass that returns the module unchanged.
    *
    * Useful as a default or no-op placeholder in conditional pipeline construction.
    * Unlike the other constructors, the identity pass does not append a trace entry.
    *
    * @tparam F the effect type (must have an [[cats.Applicative]] instance)
    * @return a `Pass[F]` that returns its input module unmodified
    */
  def id[F[_]: Applicative]: Pass[F] =
    Kleisli(m => Applicative[F].pure(m))

  /**
    * Appends a pass name to the module's trace metadata.
    *
    * @param module the module to update
    * @param name   the pass name to append
    * @return the module with the updated trace
    */
  private def appendTrace(module: Module[Fix[SemanticF]], name: String): Module[Fix[SemanticF]] = {
    val trace = module.meta.get(traceKey).getOrElse(Chain.empty)
    module.copy(meta = module.meta.set(traceKey, trace :+ name))
  }
}
