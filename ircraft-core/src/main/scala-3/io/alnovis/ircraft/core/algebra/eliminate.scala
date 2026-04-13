package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.algebra.Algebra._

/** Type-safe dialect elimination via coproduct shrinking. */
object eliminate:

  def dialect[F[_], G[_]](
    algebra: Algebra[F, Fix[G]]
  )(using T: Traverse[F :+: G]): Fix[F :+: G] => Fix[G] =
    val alg: Algebra[F :+: G, Fix[G]] = {
      case Coproduct.Inl(fop) => algebra(fop)
      case Coproduct.Inr(gop) => Fix(gop)
    }
    scheme.cata[F :+: G, Fix[G]](alg)
