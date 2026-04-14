package io.alnovis.ircraft.dialects.proto

import cats.{Applicative, Eval, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{DialectInfo, HasFields, HasMeta, HasMethods, HasName, HasNested, HasVisibility}
import io.alnovis.ircraft.core.ir.{EnumVariant, Field, Func, Meta, Visibility}

/**
  * ProtoF -- the Protocol Buffers dialect functor for ircraft.
  *
  * This functor represents protobuf-specific IR constructs that do not
  * exist in the core [[io.alnovis.ircraft.core.ir.SemanticF]] dialect:
  * messages, enums (with proto semantics), and oneofs. ProtoF is designed
  * to be composed with [[io.alnovis.ircraft.core.ir.SemanticF]] via
  * [[io.alnovis.ircraft.core.algebra.Coproduct]] to form a combined IR.
  *
  * During lowering, ProtoF nodes are eliminated into SemanticF nodes via
  * [[ProtoEliminate.eliminateProto]].
  *
  * @tparam A the recursive carrier type, replaced by
  *           [[io.alnovis.ircraft.core.algebra.Fix]] in the fixed-point representation
  * @see [[ProtoEliminate]] for the elimination (lowering) of this dialect
  * @see [[io.alnovis.ircraft.core.algebra.Coproduct]]
  */
sealed trait ProtoF[+A]

/**
  * Companion object for [[ProtoF]] providing case class variants and
  * implicit typeclass instances.
  */
object ProtoF {

  /**
    * A protobuf message node.
    *
    * @param name      the message name
    * @param fields    the message fields
    * @param functions functions (methods) associated with this message
    * @param nested    nested declarations (sub-messages, enums, etc.)
    * @param meta      extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[io.alnovis.ircraft.core.ir.Field]]
    */
  final case class MessageNodeF[+A](
    name: String,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  /**
    * A protobuf enum node.
    *
    * @param name     the enum name
    * @param variants the enum variants (values)
    * @param meta     extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[io.alnovis.ircraft.core.ir.EnumVariant]]
    */
  final case class EnumNodeF[+A](
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  /**
    * A protobuf oneof group node.
    *
    * Represents a set of mutually exclusive fields in a protobuf message.
    *
    * @param name   the oneof group name
    * @param fields the oneof member fields
    * @param meta   extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[io.alnovis.ircraft.core.ir.Field]]
    */
  final case class OneofNodeF[+A](
    name: String,
    fields: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  /**
    * Implicit [[cats.Traverse]] instance for [[ProtoF]].
    *
    * Only [[MessageNodeF]] has recursive children (via the `nested` field);
    * [[EnumNodeF]] and [[OneofNodeF]] are leaves.
    */
  implicit val protoTraverse: Traverse[ProtoF] = new Traverse[ProtoF] {
    def traverse[G[_], A, B](fa: ProtoF[A])(f: A => G[B])(implicit G: Applicative[G]): G[ProtoF[B]] = fa match {
      case MessageNodeF(n, flds, fns, nested, m) =>
        nested.traverse(f).map(ns => MessageNodeF[B](n, flds, fns, ns, m))
      case EnumNodeF(n, vs, m) =>
        G.pure(EnumNodeF[B](n, vs, m))
      case OneofNodeF(n, flds, m) =>
        G.pure(OneofNodeF[B](n, flds, m))
    }

    def foldLeft[A, B](fa: ProtoF[A], b: B)(f: (B, A) => B): B = fa match {
      case MessageNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                                => b
    }

    def foldRight[A, B](fa: ProtoF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
      case MessageNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                                => lb
    }
  }

  /**
    * Implicit [[DialectInfo]] instance for [[ProtoF]], providing
    * the dialect name ("ProtoF") and operation count (3 variants).
    */
  implicit val protoDialectInfo: DialectInfo[ProtoF] = DialectInfo("ProtoF", 3)

  /**
    * Implicit [[HasName]] instance for [[ProtoF]].
    *
    * Extracts the name from any ProtoF variant.
    */
  implicit val protoHasName: HasName[ProtoF] = new HasName[ProtoF] {
    def name[A](fa: ProtoF[A]): String = fa match {
      case MessageNodeF(n, _, _, _, _) => n
      case EnumNodeF(n, _, _)          => n
      case OneofNodeF(n, _, _)         => n
    }
  }

  /**
    * Implicit [[HasNested]] instance for [[ProtoF]].
    *
    * Returns nested declarations for [[MessageNodeF]]; returns an empty
    * vector for [[EnumNodeF]] and [[OneofNodeF]].
    */
  implicit val protoHasNested: HasNested[ProtoF] = new HasNested[ProtoF] {
    def nested[A](fa: ProtoF[A]): Vector[A] = fa match {
      case MessageNodeF(_, _, _, nested, _) => nested
      case _                                => Vector.empty
    }
  }

  /**
    * Implicit [[HasMeta]] instance for [[ProtoF]].
    *
    * Supports both reading and updating metadata on any ProtoF variant.
    */
  implicit val protoHasMeta: HasMeta[ProtoF] = new HasMeta[ProtoF] {
    def meta[A](fa: ProtoF[A]): Meta = fa match {
      case MessageNodeF(_, _, _, _, m) => m
      case EnumNodeF(_, _, m)          => m
      case OneofNodeF(_, _, m)         => m
    }
    def withMeta[A](fa: ProtoF[A], m: Meta): ProtoF[A] = fa match {
      case msg: MessageNodeF[A @unchecked] => msg.copy(meta = m)
      case enm: EnumNodeF[A @unchecked]    => enm.copy(meta = m)
      case onf: OneofNodeF[A @unchecked]   => onf.copy(meta = m)
    }
  }

  /**
    * Implicit [[HasFields]] instance for [[ProtoF]].
    *
    * Returns fields for [[MessageNodeF]] and [[OneofNodeF]]; returns an
    * empty vector for [[EnumNodeF]].
    */
  implicit val protoHasFields: HasFields[ProtoF] = new HasFields[ProtoF] {
    def fields[A](fa: ProtoF[A]): Vector[Field] = fa match {
      case MessageNodeF(_, flds, _, _, _) => flds
      case OneofNodeF(_, flds, _)         => flds
      case _                              => Vector.empty
    }
  }

  /**
    * Implicit [[HasMethods]] instance for [[ProtoF]].
    *
    * Returns functions for [[MessageNodeF]]; returns an empty vector
    * for [[EnumNodeF]] and [[OneofNodeF]].
    */
  implicit val protoHasMethods: HasMethods[ProtoF] = new HasMethods[ProtoF] {
    def functions[A](fa: ProtoF[A]): Vector[Func] = fa match {
      case MessageNodeF(_, _, fns, _, _) => fns
      case _                             => Vector.empty
    }
  }

  /**
    * Implicit [[HasVisibility]] instance for [[ProtoF]].
    *
    * All protobuf declarations are public -- protobuf has no visibility modifiers.
    */
  implicit val protoHasVisibility: HasVisibility[ProtoF] = new HasVisibility[ProtoF] {
    def visibility[A](fa: ProtoF[A]): Visibility = Visibility.Public
  }
}
