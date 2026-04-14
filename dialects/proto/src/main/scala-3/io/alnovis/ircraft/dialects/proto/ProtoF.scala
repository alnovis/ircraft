package io.alnovis.ircraft.dialects.proto

import cats.{ Applicative, Eval, Functor, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{ DialectInfo, HasFields, HasMeta, HasMethods, HasName, HasNested, HasVisibility }
import io.alnovis.ircraft.core.ir.{ EnumVariant, Field, Func, Meta, Visibility }

/**
  * Proto dialect functor -- represents proto-specific IR constructs.
  *
  * These capture proto semantics (messages, enums, oneofs) that may not map
  * cleanly to the standard SemanticF IR. Progressive lowering via
  * eliminate.dialect converts ProtoF nodes to SemanticF.
  */
enum ProtoF[+A]:

  case MessageNodeF(
    name: String,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  case EnumNodeF(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

  case OneofNodeF(
    name: String,
    fields: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends ProtoF[A]

object ProtoF:
  import ProtoF._

  // Traverse extends Functor, so no separate Functor instance needed
  given Traverse[ProtoF] with

    def traverse[G[_]: Applicative, A, B](fa: ProtoF[A])(f: A => G[B]): G[ProtoF[B]] = fa match
      case MessageNodeF(n, flds, fns, nested, m) =>
        nested.traverse(f).map(ns => MessageNodeF(n, flds, fns, ns, m))
      case EnumNodeF(n, vs, m) =>
        Applicative[G].pure(EnumNodeF(n, vs, m))
      case OneofNodeF(n, flds, m) =>
        Applicative[G].pure(OneofNodeF(n, flds, m))

    def foldLeft[A, B](fa: ProtoF[A], b: B)(f: (B, A) => B): B = fa match
      case MessageNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                                => b

    def foldRight[A, B](fa: ProtoF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case MessageNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                                => lb

  given DialectInfo[ProtoF] = DialectInfo("ProtoF", 3)

  given HasName[ProtoF] with

    def name[A](fa: ProtoF[A]): String = fa match
      case MessageNodeF(n, _, _, _, _) => n
      case EnumNodeF(n, _, _)          => n
      case OneofNodeF(n, _, _)         => n

  given HasNested[ProtoF] with

    def nested[A](fa: ProtoF[A]): Vector[A] = fa match
      case MessageNodeF(_, _, _, nested, _) => nested
      case _                                => Vector.empty

  given HasMeta[ProtoF] with

    def meta[A](fa: ProtoF[A]): Meta = fa match
      case MessageNodeF(_, _, _, _, m) => m
      case EnumNodeF(_, _, m)          => m
      case OneofNodeF(_, _, m)         => m

    def withMeta[A](fa: ProtoF[A], m: Meta): ProtoF[A] = fa match
      case msg: MessageNodeF[A @unchecked] => msg.copy(meta = m)
      case enm: EnumNodeF[A @unchecked]    => enm.copy(meta = m)
      case onf: OneofNodeF[A @unchecked]   => onf.copy(meta = m)

  given HasFields[ProtoF] with

    def fields[A](fa: ProtoF[A]): Vector[Field] = fa match
      case MessageNodeF(_, flds, _, _, _) => flds
      case OneofNodeF(_, flds, _)         => flds
      case _                              => Vector.empty

  given HasMethods[ProtoF] with

    def functions[A](fa: ProtoF[A]): Vector[Func] = fa match
      case MessageNodeF(_, _, fns, _, _) => fns
      case _                             => Vector.empty

  given HasVisibility[ProtoF] with

    def visibility[A](fa: ProtoF[A]): Visibility = Visibility.Public
