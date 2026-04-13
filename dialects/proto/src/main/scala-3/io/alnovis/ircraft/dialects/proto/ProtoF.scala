package io.alnovis.ircraft.dialects.proto

import cats.{ Applicative, Eval, Functor, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{ DialectInfo, HasName, HasNested }
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

  given Functor[ProtoF] with

    def map[A, B](fa: ProtoF[A])(f: A => B): ProtoF[B] = fa match
      case MessageNodeF(n, flds, fns, nested, m) => MessageNodeF(n, flds, fns, nested.map(f), m)
      case EnumNodeF(n, vs, m)                   => EnumNodeF(n, vs, m)
      case OneofNodeF(n, flds, m)                => OneofNodeF(n, flds, m)

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
