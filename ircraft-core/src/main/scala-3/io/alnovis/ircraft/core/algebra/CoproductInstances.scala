package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{ Field, Func, Meta, Visibility }

/**
  * Auto-derived trait mixin instances for [[Coproduct]][F, G, *].
  *
  * When two dialect functors `F` and `G` are composed via [[Coproduct]] (using
  * the [[`:+:`]] syntax), these given instances automatically derive the combined
  * [[HasName]], [[HasMeta]], [[HasFields]], [[HasMethods]], [[HasNested]], and
  * [[HasVisibility]] instances by delegating to the component instances.
  *
  * This eliminates the need for manual instance definitions when composing dialects.
  *
  * {{{
  * // Given HasName[ProtoF] and HasName[SemanticF], the compiler derives:
  * // HasName[ProtoF :+: SemanticF]
  * val name = HasName[ProtoF :+: SemanticF].name(node)
  * }}}
  *
  * @see [[SemanticInstances]] for the base instances on [[io.alnovis.ircraft.core.ir.SemanticF]]
  * @see [[Coproduct]] for the disjoint union type
  */
object CoproductInstances:

  /**
    * [[HasName]] instance for `F :+: G`, delegating to the component instances.
    *
    * @tparam F the left functor (must have a [[HasName]] instance)
    * @tparam G the right functor (must have a [[HasName]] instance)
    */
  given coproductHasName[F[_], G[_]](using F: HasName[F], G: HasName[G]): HasName[F :+: G] with

    def name[A](fa: (F :+: G)[A]): String = fa match
      case Coproduct.Inl(f) => F.name(f)
      case Coproduct.Inr(g) => G.name(g)

  /**
    * [[HasMeta]] instance for `F :+: G`, delegating to the component instances.
    *
    * Both `meta` (read) and `withMeta` (copy-with-update) are supported.
    *
    * @tparam F the left functor (must have a [[HasMeta]] instance)
    * @tparam G the right functor (must have a [[HasMeta]] instance)
    */
  given coproductHasMeta[F[_], G[_]](using F: HasMeta[F], G: HasMeta[G]): HasMeta[F :+: G] with

    def meta[A](fa: (F :+: G)[A]): Meta = fa match
      case Coproduct.Inl(f) => F.meta(f)
      case Coproduct.Inr(g) => G.meta(g)

    def withMeta[A](fa: (F :+: G)[A], m: Meta): (F :+: G)[A] = fa match
      case Coproduct.Inl(f) => Coproduct.Inl(F.withMeta(f, m))
      case Coproduct.Inr(g) => Coproduct.Inr(G.withMeta(g, m))

  /**
    * [[HasFields]] instance for `F :+: G`, delegating to the component instances.
    *
    * @tparam F the left functor (must have a [[HasFields]] instance)
    * @tparam G the right functor (must have a [[HasFields]] instance)
    */
  given coproductHasFields[F[_], G[_]](using F: HasFields[F], G: HasFields[G]): HasFields[F :+: G] with

    def fields[A](fa: (F :+: G)[A]): Vector[Field] = fa match
      case Coproduct.Inl(f) => F.fields(f)
      case Coproduct.Inr(g) => G.fields(g)

  /**
    * [[HasMethods]] instance for `F :+: G`, delegating to the component instances.
    *
    * @tparam F the left functor (must have a [[HasMethods]] instance)
    * @tparam G the right functor (must have a [[HasMethods]] instance)
    */
  given coproductHasMethods[F[_], G[_]](using F: HasMethods[F], G: HasMethods[G]): HasMethods[F :+: G] with

    def functions[A](fa: (F :+: G)[A]): Vector[Func] = fa match
      case Coproduct.Inl(f) => F.functions(f)
      case Coproduct.Inr(g) => G.functions(g)

  /**
    * [[HasNested]] instance for `F :+: G`, delegating to the component instances.
    *
    * @tparam F the left functor (must have a [[HasNested]] instance)
    * @tparam G the right functor (must have a [[HasNested]] instance)
    */
  given coproductHasNested[F[_], G[_]](using F: HasNested[F], G: HasNested[G]): HasNested[F :+: G] with

    def nested[A](fa: (F :+: G)[A]): Vector[A] = fa match
      case Coproduct.Inl(f) => F.nested(f)
      case Coproduct.Inr(g) => G.nested(g)

  /**
    * [[HasVisibility]] instance for `F :+: G`, delegating to the component instances.
    *
    * @tparam F the left functor (must have a [[HasVisibility]] instance)
    * @tparam G the right functor (must have a [[HasVisibility]] instance)
    */
  given coproductHasVisibility[F[_], G[_]](using F: HasVisibility[F], G: HasVisibility[G]): HasVisibility[F :+: G] with

    def visibility[A](fa: (F :+: G)[A]): Visibility = fa match
      case Coproduct.Inl(f) => F.visibility(f)
      case Coproduct.Inr(g) => G.visibility(g)
