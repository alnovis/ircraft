package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{Field, Func, Meta, Visibility}

/**
  * Auto-derived structural typeclass instances for `Coproduct[F, G, *]`.
  *
  * When both constituent functors `F` and `G` have a structural typeclass
  * instance (e.g., [[HasName]], [[HasMeta]]), this object provides the
  * corresponding instance for their [[Coproduct]]. Each method delegates
  * to the appropriate side based on whether the value is [[Coproduct.Inl]]
  * or [[Coproduct.Inr]].
  *
  * These instances are automatically available when this object is imported:
  * {{{
  * import io.alnovis.ircraft.core.algebra.CoproductInstances._
  * }}}
  *
  * @see [[SemanticInstances]] for the base instances on [[io.alnovis.ircraft.core.ir.SemanticF]]
  * @see [[Coproduct]] for the disjoint union type
  */
object CoproductInstances {

  /**
    * Derives a [[HasName]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * @param F the [[HasName]] instance for the left functor
    * @param G the [[HasName]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasName]] instance for the coproduct
    */
  implicit def coproductHasName[F[_], G[_]](implicit F: HasName[F], G: HasName[G]): HasName[Coproduct[F, G, *]] =
    new HasName[Coproduct[F, G, *]] {
      def name[A](fa: Coproduct[F, G, A]): String = fa match {
        case Coproduct.Inl(f) => F.name(f)
        case Coproduct.Inr(g) => G.name(g)
      }
    }

  /**
    * Derives a [[HasMeta]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * Both `meta` (read) and `withMeta` (update) are delegated to the
    * corresponding side of the coproduct.
    *
    * @param F the [[HasMeta]] instance for the left functor
    * @param G the [[HasMeta]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasMeta]] instance for the coproduct
    */
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

  /**
    * Derives a [[HasNested]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * @param F the [[HasNested]] instance for the left functor
    * @param G the [[HasNested]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasNested]] instance for the coproduct
    */
  implicit def coproductHasNested[F[_], G[_]](implicit F: HasNested[F], G: HasNested[G]): HasNested[Coproduct[F, G, *]] =
    new HasNested[Coproduct[F, G, *]] {
      def nested[A](fa: Coproduct[F, G, A]): Vector[A] = fa match {
        case Coproduct.Inl(f) => F.nested(f)
        case Coproduct.Inr(g) => G.nested(g)
      }
    }

  /**
    * Derives a [[HasFields]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * @param F the [[HasFields]] instance for the left functor
    * @param G the [[HasFields]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasFields]] instance for the coproduct
    */
  implicit def coproductHasFields[F[_], G[_]](implicit F: HasFields[F], G: HasFields[G]): HasFields[Coproduct[F, G, *]] =
    new HasFields[Coproduct[F, G, *]] {
      def fields[A](fa: Coproduct[F, G, A]): Vector[Field] = fa match {
        case Coproduct.Inl(f) => F.fields(f)
        case Coproduct.Inr(g) => G.fields(g)
      }
    }

  /**
    * Derives a [[HasMethods]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * @param F the [[HasMethods]] instance for the left functor
    * @param G the [[HasMethods]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasMethods]] instance for the coproduct
    */
  implicit def coproductHasMethods[F[_], G[_]](implicit F: HasMethods[F], G: HasMethods[G]): HasMethods[Coproduct[F, G, *]] =
    new HasMethods[Coproduct[F, G, *]] {
      def functions[A](fa: Coproduct[F, G, A]): Vector[Func] = fa match {
        case Coproduct.Inl(f) => F.functions(f)
        case Coproduct.Inr(g) => G.functions(g)
      }
    }

  /**
    * Derives a [[HasVisibility]] instance for `Coproduct[F, G, *]` from instances on `F` and `G`.
    *
    * @param F the [[HasVisibility]] instance for the left functor
    * @param G the [[HasVisibility]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[HasVisibility]] instance for the coproduct
    */
  implicit def coproductHasVisibility[F[_], G[_]](implicit F: HasVisibility[F], G: HasVisibility[G]): HasVisibility[Coproduct[F, G, *]] =
    new HasVisibility[Coproduct[F, G, *]] {
      def visibility[A](fa: Coproduct[F, G, A]): Visibility = fa match {
        case Coproduct.Inl(f) => F.visibility(f)
        case Coproduct.Inr(g) => G.visibility(g)
      }
    }
}
