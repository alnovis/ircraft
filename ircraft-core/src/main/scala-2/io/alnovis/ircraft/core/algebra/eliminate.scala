package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.algebra.Algebra._

/**
  * Type-safe dialect elimination via coproduct shrinking.
  *
  * This object provides the [[dialect]] method, which takes an algebra that
  * folds a dialect functor `F` into `Fix[G]`, and produces a function that
  * eliminates `F` from a `Coproduct[F, G, *]` fixed-point tree. The result
  * is a pure `Fix[G]` tree with the `F` dialect removed.
  *
  * This is the core mechanism for nanopass-style lowering: each elimination
  * step peels off one dialect layer from the coproduct.
  *
  * @see [[Coproduct]] for the disjoint union of functors
  * @see [[io.alnovis.ircraft.core.algebra.scheme.cata]] for the catamorphism used internally
  * @see [[Algebra]] for the F-algebra type alias
  */
object eliminate {

  /**
    * Constructs a dialect elimination function from the given algebra.
    *
    * The algebra specifies how to translate each `F` operation into a `Fix[G]`
    * tree. Operations already in `G` are re-wrapped with [[Fix]]. The resulting
    * function performs a full catamorphism over the input tree.
    *
    * @param algebra the F-algebra translating `F` operations to `Fix[G]`
    * @param T       the implicit [[cats.Traverse]] instance for `Coproduct[F, G, *]`
    * @tparam F the dialect functor to eliminate
    * @tparam G the target functor to retain
    * @return a function from `Fix[Coproduct[F, G, *]]` to `Fix[G]`
    */
  def dialect[F[_], G[_]](
    algebra: Algebra[F, Fix[G]]
  )(implicit T: Traverse[Coproduct[F, G, *]]): Fix[Coproduct[F, G, *]] => Fix[G] = {
    val alg: Algebra[Coproduct[F, G, *], Fix[G]] = {
      case Coproduct.Inl(fop) => algebra(fop)
      case Coproduct.Inr(gop) => Fix(gop)
    }
    scheme.cata[Coproduct[F, G, *], Fix[G]](alg)
  }
}
