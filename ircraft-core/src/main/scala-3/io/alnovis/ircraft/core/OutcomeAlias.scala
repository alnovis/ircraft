package io.alnovis.ircraft.core

import cats.data._

/** Convenience type alias available only on Scala 3. Scala 2 users use IorT directly. */
type Outcome[F[_], A] = IorT[F, NonEmptyChain[Diagnostic], A]
