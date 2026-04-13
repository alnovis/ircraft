package io.alnovis.ircraft.core.algebra

/** Typeclass witnessing that functor F can be injected into functor G. */
trait Inject[F[_], G[_]] {
  def inj[A](fa: F[A]): G[A]
  def prj[A](ga: G[A]): Option[F[A]]
}

object Inject {

  def apply[F[_], G[_]](implicit i: Inject[F, G]): Inject[F, G] = i

  /** F injects into itself. */
  implicit def refl[F[_]]: Inject[F, F] = new Inject[F, F] {
    def inj[A](fa: F[A]): F[A] = fa
    def prj[A](ga: F[A]): Option[F[A]] = Some(ga)
  }

  /** F injects into the left of Coproduct[F, G, *]. */
  implicit def left[F[_], G[_]]: Inject[F, Coproduct[F, G, *]] = new Inject[F, Coproduct[F, G, *]] {
    def inj[A](fa: F[A]): Coproduct[F, G, A] = Coproduct.Inl(fa)
    def prj[A](ga: Coproduct[F, G, A]): Option[F[A]] = ga match {
      case Coproduct.Inl(fa) => Some(fa)
      case _                 => None
    }
  }

  /** If F injects into G, then F injects into Coproduct[H, G, *] (on the right). */
  implicit def right[F[_], G[_], H[_]](implicit fg: Inject[F, G]): Inject[F, Coproduct[H, G, *]] =
    new Inject[F, Coproduct[H, G, *]] {
      def inj[A](fa: F[A]): Coproduct[H, G, A] = Coproduct.Inr(fg.inj(fa))
      def prj[A](ga: Coproduct[H, G, A]): Option[F[A]] = ga match {
        case Coproduct.Inr(ga) => fg.prj(ga)
        case _                 => None
      }
    }
}
