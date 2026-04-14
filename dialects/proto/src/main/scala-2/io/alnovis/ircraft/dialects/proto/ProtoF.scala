package io.alnovis.ircraft.dialects.proto

import cats.{Applicative, Eval, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{DialectInfo, HasFields, HasMeta, HasMethods, HasName, HasNested, HasVisibility}
import io.alnovis.ircraft.core.ir.{EnumVariant, Field, Func, Meta, Visibility}

sealed trait ProtoF[+A]

object ProtoF {
  final case class MessageNodeF[+A](
    name: String,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  final case class EnumNodeF[+A](
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  final case class OneofNodeF[+A](
    name: String,
    fields: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

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

  implicit val protoDialectInfo: DialectInfo[ProtoF] = DialectInfo("ProtoF", 3)

  implicit val protoHasName: HasName[ProtoF] = new HasName[ProtoF] {
    def name[A](fa: ProtoF[A]): String = fa match {
      case MessageNodeF(n, _, _, _, _) => n
      case EnumNodeF(n, _, _)          => n
      case OneofNodeF(n, _, _)         => n
    }
  }

  implicit val protoHasNested: HasNested[ProtoF] = new HasNested[ProtoF] {
    def nested[A](fa: ProtoF[A]): Vector[A] = fa match {
      case MessageNodeF(_, _, _, nested, _) => nested
      case _                                => Vector.empty
    }
  }

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

  implicit val protoHasFields: HasFields[ProtoF] = new HasFields[ProtoF] {
    def fields[A](fa: ProtoF[A]): Vector[Field] = fa match {
      case MessageNodeF(_, flds, _, _, _) => flds
      case OneofNodeF(_, flds, _)         => flds
      case _                              => Vector.empty
    }
  }

  implicit val protoHasMethods: HasMethods[ProtoF] = new HasMethods[ProtoF] {
    def functions[A](fa: ProtoF[A]): Vector[Func] = fa match {
      case MessageNodeF(_, _, fns, _, _) => fns
      case _                             => Vector.empty
    }
  }

  implicit val protoHasVisibility: HasVisibility[ProtoF] = new HasVisibility[ProtoF] {
    def visibility[A](fa: ProtoF[A]): Visibility = Visibility.Public
  }
}
