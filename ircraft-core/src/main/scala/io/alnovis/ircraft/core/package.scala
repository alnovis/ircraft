package io.alnovis.ircraft

import cats.data._
import io.alnovis.ircraft.core.ir.Module

package object core {
  type Pass[F[_]]             = Kleisli[F, Module, Module]
  type Lowering[F[_], Source] = Kleisli[F, Source, Module]
  // Outcome type alias only in scala-3 (Scala 2 uses IorT directly due to type inference limitations)
}
