package io.alnovis.ircraft.core

import cats.data._

/**
  * Scala 3 convenience type alias for the ircraft error/warning monad.
  *
  * `Outcome[F, A]` is an `IorT`-based monad transformer that supports three result states:
  *   - '''Success''' (`Ior.Right`): computation succeeded with value `A`, no diagnostics.
  *   - '''Warning''' (`Ior.Both`): computation succeeded with value `A`, but produced non-fatal diagnostics.
  *   - '''Failure''' (`Ior.Left`): computation failed with one or more error diagnostics; no value.
  *
  * Diagnostics are accumulated as `NonEmptyChain[Diagnostic]`.
  *
  * On Scala 2, this type alias is not available; use `IorT[F, NonEmptyChain[Diagnostic], A]` directly.
  *
  * @tparam F the base effect type (e.g., `cats.Id`, `cats.Eval`, `IO`)
  * @tparam A the success value type
  * @see [[Outcome$]] for smart constructors (`ok`, `warn`, `fail`, `failNec`, etc.)
  * @see [[Diagnostic]] for the diagnostic type carried in the left channel
  */
type Outcome[F[_], A] = IorT[F, NonEmptyChain[Diagnostic], A]
