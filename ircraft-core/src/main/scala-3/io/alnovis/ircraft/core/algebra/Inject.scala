package io.alnovis.ircraft.core.algebra

/**
  * Typeclass witnessing that functor `F` can be injected into functor `G`.
  *
  * This is a key component of the "Data Types a la Carte" encoding. Given
  * `Inject[F, G]`, any `F[A]` value can be lifted into `G[A]`, and any `G[A]`
  * value can be projected back to `Option[F[A]]`.
  *
  * The compiler resolves `Inject` instances automatically for [[Coproduct]]-based
  * types via the [[Inject.refl]], [[Inject.left]], and [[Inject.right]] given instances.
  *
  * {{{
  * // ProtoF injects into ProtoF :+: SemanticF
  * val node: (ProtoF :+: SemanticF)[A] = Inject[ProtoF, ProtoF :+: SemanticF].inj(protoNode)
  * }}}
  *
  * @tparam F the source functor to inject
  * @tparam G the target functor (typically a [[Coproduct]])
  * @see [[Coproduct]] for the disjoint union type
  */
trait Inject[F[_], G[_]]:

  /**
    * Injects an `F[A]` value into `G[A]`.
    *
    * @tparam A the recursive position type
    * @param fa the source functor value
    * @return the value lifted into the target functor
    */
  def inj[A](fa: F[A]): G[A]

  /**
    * Projects a `G[A]` value back to `Option[F[A]]`.
    *
    * Returns `Some` if the value is an `F[A]` inside the coproduct, `None` otherwise.
    *
    * @tparam A the recursive position type
    * @param ga the target functor value to project
    * @return `Some(fa)` if `ga` wraps an `F[A]`, `None` otherwise
    */
  def prj[A](ga: G[A]): Option[F[A]]

/**
  * Companion object for [[Inject]] providing the summoner and given instances
  * for automatic resolution of injection witnesses.
  */
object Inject:

  /**
    * Summoner for [[Inject]] instances.
    *
    * @tparam F the source functor
    * @tparam G the target functor
    * @return the implicitly resolved [[Inject]] instance
    */
  def apply[F[_], G[_]](using i: Inject[F, G]): Inject[F, G] = i

  /**
    * Reflexive injection: `F` injects into itself.
    *
    * Injection is the identity function; projection always succeeds.
    *
    * @tparam F the functor type
    */
  given refl[F[_]]: Inject[F, F] with
    def inj[A](fa: F[A]): F[A]         = fa
    def prj[A](ga: F[A]): Option[F[A]] = Some(ga)

  /**
    * Left injection: `F` injects into the left side of `F :+: G`.
    *
    * @tparam F the left functor
    * @tparam G the right functor
    */
  given left[F[_], G[_]]: Inject[F, F :+: G] with
    def inj[A](fa: F[A]): (F :+: G)[A] = Coproduct.Inl(fa)

    def prj[A](ga: (F :+: G)[A]): Option[F[A]] = ga match
      case Coproduct.Inl(fa) => Some(fa)
      case _                 => None

  /**
    * Right-recursive injection: if `F` injects into `G`, then `F` injects into `H :+: G`.
    *
    * This instance enables automatic resolution for deeply nested coproducts.
    * For example, if `F` injects into `G`, then `F` injects into `H :+: G`,
    * and by induction into `H1 :+: H2 :+: ... :+: G`.
    *
    * @tparam F the source functor
    * @tparam G the target functor that `F` already injects into
    * @tparam H the left component of the new coproduct
    */
  given right[F[_], G[_], H[_]](using fg: Inject[F, G]): Inject[F, H :+: G] with
    def inj[A](fa: F[A]): (H :+: G)[A] = Coproduct.Inr(fg.inj(fa))

    def prj[A](ga: (H :+: G)[A]): Option[F[A]] = ga match
      case Coproduct.Inr(ga) => fg.prj(ga)
      case _                 => None
