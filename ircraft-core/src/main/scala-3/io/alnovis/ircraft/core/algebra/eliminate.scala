package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.algebra.Algebra._

/**
  * Type-safe dialect elimination via coproduct shrinking.
  *
  * The `eliminate` object provides the [[dialect]] method, which constructs a
  * function that removes a dialect functor `F` from a coproduct `F :+: G` by
  * folding (catamorphism) over the fixed-point tree. Each `F`-node is lowered
  * into `Fix[G]` via the provided algebra, while `G`-nodes are re-wrapped unchanged.
  *
  * This is the fundamental mechanism for progressive lowering in ircraft:
  * {{{
  * // Lower ProtoF nodes into SemanticF:
  * val lower: Fix[ProtoF :+: SemanticF] => Fix[SemanticF] =
  *   eliminate.dialect(protoToSemanticAlgebra)
  * }}}
  *
  * @see [[Coproduct]] for the disjoint union type
  * @see [[io.alnovis.ircraft.core.algebra.scheme.cata]] for the catamorphism used internally
  * @see [[Algebra]] for the F-algebra type alias
  */
object eliminate:

  /**
    * Constructs a function that eliminates dialect `F` from a `F :+: G` coproduct.
    *
    * Given an algebra `F[Fix[G]] => Fix[G]` that lowers `F`-nodes into `G`-trees,
    * this method builds a catamorphism over `Fix[F :+: G]` that:
    *   - Applies the algebra to `F`-nodes (left side of the coproduct).
    *   - Re-wraps `G`-nodes unchanged (right side of the coproduct).
    *
    * @tparam F the dialect functor to eliminate (left side of the coproduct)
    * @tparam G the target functor to preserve (right side of the coproduct)
    * @param algebra the lowering algebra that converts `F`-nodes into `Fix[G]` trees
    * @param T       the `Traverse` instance for `F :+: G` (derived automatically from `Traverse[F]` and `Traverse[G]`)
    * @return a function `Fix[F :+: G] => Fix[G]` that eliminates all `F`-nodes
    */
  def dialect[F[_], G[_]](
    algebra: Algebra[F, Fix[G]]
  )(using T: Traverse[F :+: G]): Fix[F :+: G] => Fix[G] =
    val alg: Algebra[F :+: G, Fix[G]] = {
      case Coproduct.Inl(fop) => algebra(fop)
      case Coproduct.Inr(gop) => Fix(gop)
    }
    scheme.cata[F :+: G, Fix[G]](alg)
