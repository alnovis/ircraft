package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{ Field, Func, Meta, Visibility }

/**
  * Auto-derived trait mixin instances for Coproduct[F, G, *].
  * Delegates to the component instances.
  */
object CoproductInstances:

  given coproductHasName[F[_], G[_]](using F: HasName[F], G: HasName[G]): HasName[F :+: G] with

    def name[A](fa: (F :+: G)[A]): String = fa match
      case Coproduct.Inl(f) => F.name(f)
      case Coproduct.Inr(g) => G.name(g)

  given coproductHasMeta[F[_], G[_]](using F: HasMeta[F], G: HasMeta[G]): HasMeta[F :+: G] with

    def meta[A](fa: (F :+: G)[A]): Meta = fa match
      case Coproduct.Inl(f) => F.meta(f)
      case Coproduct.Inr(g) => G.meta(g)

    def withMeta[A](fa: (F :+: G)[A], m: Meta): (F :+: G)[A] = fa match
      case Coproduct.Inl(f) => Coproduct.Inl(F.withMeta(f, m))
      case Coproduct.Inr(g) => Coproduct.Inr(G.withMeta(g, m))

  given coproductHasFields[F[_], G[_]](using F: HasFields[F], G: HasFields[G]): HasFields[F :+: G] with

    def fields[A](fa: (F :+: G)[A]): Vector[Field] = fa match
      case Coproduct.Inl(f) => F.fields(f)
      case Coproduct.Inr(g) => G.fields(g)

  given coproductHasMethods[F[_], G[_]](using F: HasMethods[F], G: HasMethods[G]): HasMethods[F :+: G] with

    def functions[A](fa: (F :+: G)[A]): Vector[Func] = fa match
      case Coproduct.Inl(f) => F.functions(f)
      case Coproduct.Inr(g) => G.functions(g)

  given coproductHasNested[F[_], G[_]](using F: HasNested[F], G: HasNested[G]): HasNested[F :+: G] with

    def nested[A](fa: (F :+: G)[A]): Vector[A] = fa match
      case Coproduct.Inl(f) => F.nested(f)
      case Coproduct.Inr(g) => G.nested(g)

  given coproductHasVisibility[F[_], G[_]](using F: HasVisibility[F], G: HasVisibility[G]): HasVisibility[F :+: G] with

    def visibility[A](fa: (F :+: G)[A]): Visibility = fa match
      case Coproduct.Inl(f) => F.visibility(f)
      case Coproduct.Inr(g) => G.visibility(g)
