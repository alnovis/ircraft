package io.alnovis.ircraft

import cats.data._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Module, SemanticF }

package object core {
  type Pass[F[_]]             = Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]
  type Lowering[F[_], Source] = Kleisli[F, Source, Module[Fix[SemanticF]]]
  // Outcome type alias only in scala-3 (Scala 2 uses IorT directly due to type inference limitations)
}
