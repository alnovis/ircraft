package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{Field, Func, Meta, Visibility}

/** Auto-derived trait mixin instances for Coproduct[F, G, *]. */
object CoproductInstances {

  implicit def coproductHasName[F[_], G[_]](implicit F: HasName[F], G: HasName[G]): HasName[Coproduct[F, G, *]] =
    new HasName[Coproduct[F, G, *]] {
      def name[A](fa: Coproduct[F, G, A]): String = fa match {
        case Coproduct.Inl(f) => F.name(f)
        case Coproduct.Inr(g) => G.name(g)
      }
    }

  implicit def coproductHasMeta[F[_], G[_]](implicit F: HasMeta[F], G: HasMeta[G]): HasMeta[Coproduct[F, G, *]] =
    new HasMeta[Coproduct[F, G, *]] {
      def meta[A](fa: Coproduct[F, G, A]): Meta = fa match {
        case Coproduct.Inl(f) => F.meta(f)
        case Coproduct.Inr(g) => G.meta(g)
      }
      def withMeta[A](fa: Coproduct[F, G, A], m: Meta): Coproduct[F, G, A] = fa match {
        case Coproduct.Inl(f) => Coproduct.Inl(F.withMeta(f, m))
        case Coproduct.Inr(g) => Coproduct.Inr(G.withMeta(g, m))
      }
    }

  implicit def coproductHasNested[F[_], G[_]](implicit F: HasNested[F], G: HasNested[G]): HasNested[Coproduct[F, G, *]] =
    new HasNested[Coproduct[F, G, *]] {
      def nested[A](fa: Coproduct[F, G, A]): Vector[A] = fa match {
        case Coproduct.Inl(f) => F.nested(f)
        case Coproduct.Inr(g) => G.nested(g)
      }
    }

  implicit def coproductHasFields[F[_], G[_]](implicit F: HasFields[F], G: HasFields[G]): HasFields[Coproduct[F, G, *]] =
    new HasFields[Coproduct[F, G, *]] {
      def fields[A](fa: Coproduct[F, G, A]): Vector[Field] = fa match {
        case Coproduct.Inl(f) => F.fields(f)
        case Coproduct.Inr(g) => G.fields(g)
      }
    }

  implicit def coproductHasMethods[F[_], G[_]](implicit F: HasMethods[F], G: HasMethods[G]): HasMethods[Coproduct[F, G, *]] =
    new HasMethods[Coproduct[F, G, *]] {
      def functions[A](fa: Coproduct[F, G, A]): Vector[Func] = fa match {
        case Coproduct.Inl(f) => F.functions(f)
        case Coproduct.Inr(g) => G.functions(g)
      }
    }

  implicit def coproductHasVisibility[F[_], G[_]](implicit F: HasVisibility[F], G: HasVisibility[G]): HasVisibility[Coproduct[F, G, *]] =
    new HasVisibility[Coproduct[F, G, *]] {
      def visibility[A](fa: Coproduct[F, G, A]): Visibility = fa match {
        case Coproduct.Inl(f) => F.visibility(f)
        case Coproduct.Inr(g) => G.visibility(g)
      }
    }
}
