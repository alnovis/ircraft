package io.alnovis.ircraft.core.algebra

/**
  * Typeclass witnessing that functor `F` can be injected into functor `G`.
  *
  * This is the key mechanism for working with extensible coproduct functors
  * in the "Data Types a la Carte" style. Given `Inject[F, G]`, values of
  * type `F[A]` can be lifted into `G[A]`, and values of type `G[A]` can be
  * projected back to `Option[F[A]]`.
  *
  * Three implicit instances are provided in the companion object:
  *   - [[Inject.refl]]: `F` injects into itself (identity)
  *   - [[Inject.left]]: `F` injects into `Coproduct[F, G, *]` (left branch)
  *   - [[Inject.right]]: if `F` injects into `G`, then `F` injects into
  *     `Coproduct[H, G, *]` (recursive right branch)
  *
  * @tparam F the source functor to inject
  * @tparam G the target functor (typically a [[Coproduct]])
  * @see [[Coproduct]]
  */
trait Inject[F[_], G[_]] {

  /**
    * Injects a value of `F[A]` into `G[A]`.
    *
    * @param fa the source functor value
    * @tparam A the carrier type
    * @return the value lifted into the target functor
    */
  def inj[A](fa: F[A]): G[A]

  /**
    * Projects a value of `G[A]` back to `Option[F[A]]`.
    *
    * @param ga the target functor value to project from
    * @tparam A the carrier type
    * @return `Some(fa)` if `ga` contains an `F[A]` value, `None` otherwise
    */
  def prj[A](ga: G[A]): Option[F[A]]
}

/**
  * Companion object for [[Inject]] providing the summoner method
  * and implicit instance derivations for coproduct injection.
  */
object Inject {

  /**
    * Summoner method for obtaining an [[Inject]] instance.
    *
    * @tparam F the source functor
    * @tparam G the target functor
    * @return the implicit [[Inject]] instance
    */
  def apply[F[_], G[_]](implicit i: Inject[F, G]): Inject[F, G] = i

  /**
    * Reflexive injection: any functor `F` can be injected into itself.
    *
    * Injection is the identity function; projection always succeeds.
    *
    * @tparam F the functor type
    * @return an [[Inject]] instance where `F` injects into `F`
    */
  implicit def refl[F[_]]: Inject[F, F] = new Inject[F, F] {
    def inj[A](fa: F[A]): F[A] = fa
    def prj[A](ga: F[A]): Option[F[A]] = Some(ga)
  }

  /**
    * Left injection: `F` injects into `Coproduct[F, G, *]` via [[Coproduct.Inl]].
    *
    * @tparam F the left functor (source)
    * @tparam G the right functor
    * @return an [[Inject]] instance for the left branch of a [[Coproduct]]
    */
  implicit def left[F[_], G[_]]: Inject[F, Coproduct[F, G, *]] = new Inject[F, Coproduct[F, G, *]] {
    def inj[A](fa: F[A]): Coproduct[F, G, A] = Coproduct.Inl(fa)
    def prj[A](ga: Coproduct[F, G, A]): Option[F[A]] = ga match {
      case Coproduct.Inl(fa) => Some(fa)
      case _                 => None
    }
  }

  /**
    * Right-recursive injection: if `F` injects into `G`, then `F` also injects
    * into `Coproduct[H, G, *]` via [[Coproduct.Inr]].
    *
    * This instance enables injection into arbitrarily nested coproducts by
    * recursively searching the right branch.
    *
    * @param fg the existing [[Inject]] instance witnessing `F` into `G`
    * @tparam F the source functor
    * @tparam G the right functor (where `F` is already injectable)
    * @tparam H the left functor of the outer [[Coproduct]]
    * @return an [[Inject]] instance for the right branch of a [[Coproduct]]
    */
  implicit def right[F[_], G[_], H[_]](implicit fg: Inject[F, G]): Inject[F, Coproduct[H, G, *]] =
    new Inject[F, Coproduct[H, G, *]] {
      def inj[A](fa: F[A]): Coproduct[H, G, A] = Coproduct.Inr(fg.inj(fa))
      def prj[A](ga: Coproduct[H, G, A]): Option[F[A]] = ga match {
        case Coproduct.Inr(ga) => fg.prj(ga)
        case _                 => None
      }
    }
}
