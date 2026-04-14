package io.alnovis.ircraft.dialects.proto

import cats.{ Applicative, Eval, Functor, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{
  DialectInfo,
  HasFields,
  HasMeta,
  HasMethods,
  HasName,
  HasNested,
  HasVisibility
}
import io.alnovis.ircraft.core.ir.{ EnumVariant, Field, Func, Meta, Visibility }

/**
  * Proto dialect functor -- represents Protocol Buffers-specific IR constructs.
  *
  * These capture proto semantics (messages, enums, oneofs) that may not map
  * cleanly to the standard [[io.alnovis.ircraft.core.ir.SemanticF]] IR. Progressive lowering via
  * [[io.alnovis.ircraft.core.algebra.eliminate.dialect]] converts [[ProtoF]] nodes to
  * [[io.alnovis.ircraft.core.ir.SemanticF]].
  *
  * The three operations are:
  *   - [[ProtoF.MessageNodeF]] -- a proto message (struct with fields, nested types)
  *   - [[ProtoF.EnumNodeF]] -- a proto enum with named variants
  *   - [[ProtoF.OneofNodeF]] -- a proto oneof group (mutually exclusive fields)
  *
  * @tparam A the recursive position type (typically [[io.alnovis.ircraft.core.algebra.Fix]][ProtoF :+: SemanticF])
  * @see [[ProtoEliminate]] for the elimination function that lowers ProtoF to SemanticF
  * @see [[ProtoDialect]] for the lowering algebra
  * @see [[io.alnovis.ircraft.core.algebra.Coproduct]] for dialect composition
  */
enum ProtoF[+A]:

  /**
    * A Protocol Buffers message declaration.
    *
    * @param name      the message name
    * @param fields    the message fields
    * @param functions generated functions (e.g., builders, converters)
    * @param nested    nested (child) declarations at the recursive position
    * @param meta      metadata map
    */
  case MessageNodeF(
    name: String,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  /**
    * A Protocol Buffers enum declaration.
    *
    * @param name     the enum name
    * @param variants the enum variants (values)
    * @param meta     metadata map
    */
  case EnumNodeF(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  /**
    * A Protocol Buffers oneof group (mutually exclusive fields).
    *
    * @param name   the oneof group name
    * @param fields the alternative fields within the oneof
    * @param meta   metadata map
    */
  case OneofNodeF(
    name: String,
    fields: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

/**
  * Companion object for [[ProtoF]] providing typeclass instances.
  *
  * Includes `cats.Traverse`, [[DialectInfo]], and all trait mixin instances
  * ([[HasName]], [[HasNested]], [[HasMeta]], [[HasFields]], [[HasMethods]], [[HasVisibility]]).
  */
object ProtoF:
  import ProtoF._

  // Traverse extends Functor, so no separate Functor instance needed
  /**
    * `cats.Traverse` instance for [[ProtoF]].
    *
    * Only [[MessageNodeF]] contains recursive children (via `nested`);
    * [[EnumNodeF]] and [[OneofNodeF]] are leaves.
    */
  given Traverse[ProtoF] with

    /**
      * Traverses each recursive child through an effectful function and reassembles the node.
      *
      * @tparam G the applicative effect type
      * @tparam A the input recursive position type
      * @tparam B the output recursive position type
      * @param fa the proto functor node to traverse
      * @param f  the effectful transformation to apply to each recursive child
      * @return the transformed node wrapped in `G`
      */
    def traverse[G[_]: Applicative, A, B](fa: ProtoF[A])(f: A => G[B]): G[ProtoF[B]] = fa match
      case MessageNodeF(n, flds, fns, nested, m) =>
        nested.traverse(f).map(ns => MessageNodeF(n, flds, fns, ns, m))
      case EnumNodeF(n, vs, m) =>
        Applicative[G].pure(EnumNodeF(n, vs, m))
      case OneofNodeF(n, flds, m) =>
        Applicative[G].pure(OneofNodeF(n, flds, m))

    /**
      * Left-associative fold over recursive children.
      *
      * @tparam A the recursive child type
      * @tparam B the accumulator type
      * @param fa the node to fold over
      * @param b  the initial accumulator value
      * @param f  the combining function
      * @return the final accumulated value
      */
    def foldLeft[A, B](fa: ProtoF[A], b: B)(f: (B, A) => B): B = fa match
      case MessageNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                                => b

    /**
      * Right-associative lazy fold over recursive children.
      *
      * @tparam A the recursive child type
      * @tparam B the result type
      * @param fa the node to fold over
      * @param lb the lazy initial value
      * @param f  the combining function
      * @return the lazily computed result
      */
    def foldRight[A, B](fa: ProtoF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case MessageNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                                => lb

  /** [[DialectInfo]] instance identifying ProtoF with 3 operations. */
  given DialectInfo[ProtoF] = DialectInfo("ProtoF", 3)

  /** [[HasName]] instance extracting the name from any [[ProtoF]] variant. */
  given HasName[ProtoF] with

    def name[A](fa: ProtoF[A]): String = fa match
      case MessageNodeF(n, _, _, _, _) => n
      case EnumNodeF(n, _, _)          => n
      case OneofNodeF(n, _, _)         => n

  /**
    * [[HasNested]] instance for [[ProtoF]].
    *
    * Only [[MessageNodeF]] may contain nested declarations;
    * other variants return an empty vector.
    */
  given HasNested[ProtoF] with

    def nested[A](fa: ProtoF[A]): Vector[A] = fa match
      case MessageNodeF(_, _, _, nested, _) => nested
      case _                                => Vector.empty

  /**
    * [[HasMeta]] instance for [[ProtoF]], providing both read access and
    * copy-with-update for [[io.alnovis.ircraft.core.ir.Meta]].
    */
  given HasMeta[ProtoF] with

    def meta[A](fa: ProtoF[A]): Meta = fa match
      case MessageNodeF(_, _, _, _, m) => m
      case EnumNodeF(_, _, m)          => m
      case OneofNodeF(_, _, m)         => m

    def withMeta[A](fa: ProtoF[A], m: Meta): ProtoF[A] = fa match
      case msg: MessageNodeF[A @unchecked] => msg.copy(meta = m)
      case enm: EnumNodeF[A @unchecked]    => enm.copy(meta = m)
      case onf: OneofNodeF[A @unchecked]   => onf.copy(meta = m)

  /**
    * [[HasFields]] instance for [[ProtoF]].
    *
    * [[MessageNodeF]] and [[OneofNodeF]] carry fields;
    * [[EnumNodeF]] returns an empty vector.
    */
  given HasFields[ProtoF] with

    def fields[A](fa: ProtoF[A]): Vector[Field] = fa match
      case MessageNodeF(_, flds, _, _, _) => flds
      case OneofNodeF(_, flds, _)         => flds
      case _                              => Vector.empty

  /**
    * [[HasMethods]] instance for [[ProtoF]].
    *
    * Only [[MessageNodeF]] may carry generated functions;
    * other variants return an empty vector.
    */
  given HasMethods[ProtoF] with

    def functions[A](fa: ProtoF[A]): Vector[Func] = fa match
      case MessageNodeF(_, _, fns, _, _) => fns
      case _                             => Vector.empty

  /**
    * [[HasVisibility]] instance for [[ProtoF]].
    *
    * All proto declarations are public by convention, so this always
    * returns [[io.alnovis.ircraft.core.ir.Visibility.Public]].
    */
  given HasVisibility[ProtoF] with

    def visibility[A](fa: ProtoF[A]): Visibility = Visibility.Public
