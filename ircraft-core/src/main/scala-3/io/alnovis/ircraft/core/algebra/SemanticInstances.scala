package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/** Trait mixin instances for SemanticF. */
object SemanticInstances:

  given HasName[SemanticF] with
    def name[A](fa: SemanticF[A]): String = SemanticF.name(fa)

  given HasMeta[SemanticF] with
    def meta[A](fa: SemanticF[A]): Meta                      = SemanticF.meta(fa)
    def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = SemanticF.withMeta(fa, m)

  given HasFields[SemanticF] with

    def fields[A](fa: SemanticF[A]): Vector[Field] = fa match
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) => fields
      case _                                            => Vector.empty

  given HasMethods[SemanticF] with

    def functions[A](fa: SemanticF[A]): Vector[Func] = fa match
      case TypeDeclF(_, _, _, fns, _, _, _, _, _, _) => fns
      case EnumDeclF(_, _, fns, _, _, _, _)          => fns
      case FuncDeclF(fn, _)                          => Vector(fn)
      case _                                         => Vector.empty

  given HasNested[SemanticF] with

    def nested[A](fa: SemanticF[A]): Vector[A] = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested
      case _                                            => Vector.empty

  given HasVisibility[SemanticF] with

    def visibility[A](fa: SemanticF[A]): Visibility = fa match
      case TypeDeclF(_, _, _, _, _, _, _, v, _, _) => v
      case EnumDeclF(_, _, _, _, v, _, _)          => v
      case AliasDeclF(_, _, v, _)                  => v
      case ConstDeclF(_, _, _, v, _)               => v
      case FuncDeclF(fn, _)                        => fn.visibility
