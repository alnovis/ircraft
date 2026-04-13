package io.alnovis.ircraft.core.algebra

/** Typeclass witnessing that functor F can be injected into functor G. */
trait Inject[F[_], G[_]]:
  def inj[A](fa: F[A]): G[A]
  def prj[A](ga: G[A]): Option[F[A]]

object Inject:

  def apply[F[_], G[_]](using i: Inject[F, G]): Inject[F, G] = i

  /** F injects into itself. */
  given refl[F[_]]: Inject[F, F] with
    def inj[A](fa: F[A]): F[A] = fa
    def prj[A](ga: F[A]): Option[F[A]] = Some(ga)

  /** F injects into the left of F :+: G. */
  given left[F[_], G[_]]: Inject[F, F :+: G] with
    def inj[A](fa: F[A]): (F :+: G)[A] = Coproduct.Inl(fa)
    def prj[A](ga: (F :+: G)[A]): Option[F[A]] = ga match
      case Coproduct.Inl(fa) => Some(fa)
      case _                 => None

  /** If F injects into G, then F injects into H :+: G (on the right). */
  given right[F[_], G[_], H[_]](using fg: Inject[F, G]): Inject[F, H :+: G] with
    def inj[A](fa: F[A]): (H :+: G)[A] = Coproduct.Inr(fg.inj(fa))
    def prj[A](ga: (H :+: G)[A]): Option[F[A]] = ga match
      case Coproduct.Inr(ga) => fg.prj(ga)
      case _                 => None
