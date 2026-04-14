package io.alnovis.ircraft

import cats.data._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Module, SemanticF }

/**
  * Core package object defining the fundamental type aliases for the ircraft framework.
  *
  * These aliases provide concise names for the Kleisli-based pipeline types that
  * form the backbone of the ircraft compilation model:
  *
  *  - '''Pass''': a module-to-module transformation (nanopass-style)
  *  - '''Lowering''': an external-source-to-module conversion (entry point)
  *  - '''Outcome''' (Scala 3 only): `IorT`-based error/warning accumulation
  *
  * @see [[Pass$]] for factory methods to create passes
  * @see [[Lowering$]] for factory methods to create lowerings
  * @see [[Outcome$]] for smart constructors for diagnostic-carrying results
  */
package object core {

  /**
    * A nanopass-style transformation over a semantic IR module.
    *
    * Defined as `Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]`, which
    * is an effectful endomorphism on the standard semantic module type. Passes compose
    * sequentially via Kleisli `andThen`.
    *
    * @tparam F the effect type (e.g., `Id`, `Eval`, `IO`, or `IorT[F, Diags, *]`)
    */
  type Pass[F[_]] = Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]

  /**
    * An effectful conversion from an external source type to the semantic IR.
    *
    * Defined as `Kleisli[F, Source, Module[Fix[SemanticF]]]`. This is the entry
    * point of an ircraft pipeline, converting protobuf descriptors, OpenAPI specs,
    * or other input formats into the standard IR representation.
    *
    * @tparam F      the effect type
    * @tparam Source the external source representation type
    */
  type Lowering[F[_], Source] = Kleisli[F, Source, Module[Fix[SemanticF]]]
  // Outcome type alias only in scala-3 (Scala 2 uses IorT directly due to type inference limitations)
}
