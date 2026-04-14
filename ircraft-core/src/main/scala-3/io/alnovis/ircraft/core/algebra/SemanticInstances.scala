package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/**
  * Trait mixin (typeclass) instances for [[io.alnovis.ircraft.core.ir.SemanticF]].
  *
  * Provides given instances of [[HasName]], [[HasMeta]], [[HasFields]], [[HasMethods]],
  * [[HasNested]], and [[HasVisibility]] for the core [[io.alnovis.ircraft.core.ir.SemanticF]]
  * dialect functor. These instances enable uniform access to common declaration properties
  * regardless of the specific [[io.alnovis.ircraft.core.ir.SemanticF]] variant.
  *
  * When [[io.alnovis.ircraft.core.ir.SemanticF]] is composed with other dialects via
  * [[Coproduct]], the [[CoproductInstances]] automatically derive the combined instances.
  *
  * @see [[CoproductInstances]] for auto-derived instances on coproducts
  * @see [[io.alnovis.ircraft.core.ir.SemanticF]] for the dialect functor itself
  */
object SemanticInstances:

  /** [[HasName]] instance extracting the name from any [[io.alnovis.ircraft.core.ir.SemanticF]] variant. */
  given HasName[SemanticF] with
    def name[A](fa: SemanticF[A]): String = SemanticF.name(fa)

  /**
    * [[HasMeta]] instance for [[io.alnovis.ircraft.core.ir.SemanticF]], providing
    * both read access and copy-with-update for [[io.alnovis.ircraft.core.ir.Meta]].
    */
  given HasMeta[SemanticF] with
    def meta[A](fa: SemanticF[A]): Meta                      = SemanticF.meta(fa)
    def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = SemanticF.withMeta(fa, m)

  /**
    * [[HasFields]] instance for [[io.alnovis.ircraft.core.ir.SemanticF]].
    *
    * Only [[io.alnovis.ircraft.core.ir.SemanticF.TypeDeclF]] carries fields;
    * all other variants return an empty vector.
    */
  given HasFields[SemanticF] with

    def fields[A](fa: SemanticF[A]): Vector[Field] = fa match
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) => fields
      case _                                            => Vector.empty

  /**
    * [[HasMethods]] instance for [[io.alnovis.ircraft.core.ir.SemanticF]].
    *
    * Returns functions from [[io.alnovis.ircraft.core.ir.SemanticF.TypeDeclF]],
    * [[io.alnovis.ircraft.core.ir.SemanticF.EnumDeclF]], and
    * [[io.alnovis.ircraft.core.ir.SemanticF.FuncDeclF]].
    */
  given HasMethods[SemanticF] with

    def functions[A](fa: SemanticF[A]): Vector[Func] = fa match
      case TypeDeclF(_, _, _, fns, _, _, _, _, _, _) => fns
      case EnumDeclF(_, _, fns, _, _, _, _)          => fns
      case FuncDeclF(fn, _)                          => Vector(fn)
      case _                                         => Vector.empty

  /**
    * [[HasNested]] instance for [[io.alnovis.ircraft.core.ir.SemanticF]].
    *
    * Only [[io.alnovis.ircraft.core.ir.SemanticF.TypeDeclF]] may contain nested
    * (child) declarations; all other variants return an empty vector.
    */
  given HasNested[SemanticF] with

    def nested[A](fa: SemanticF[A]): Vector[A] = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested
      case _                                            => Vector.empty

  /**
    * [[HasVisibility]] instance for [[io.alnovis.ircraft.core.ir.SemanticF]].
    *
    * Returns the visibility for variants that carry it. For
    * [[io.alnovis.ircraft.core.ir.SemanticF.FuncDeclF]], the visibility is
    * delegated to the contained [[io.alnovis.ircraft.core.ir.Func]].
    */
  given HasVisibility[SemanticF] with

    def visibility[A](fa: SemanticF[A]): Visibility = fa match
      case TypeDeclF(_, _, _, _, _, _, _, v, _, _) => v
      case EnumDeclF(_, _, _, _, v, _, _)          => v
      case AliasDeclF(_, _, v, _)                  => v
      case ConstDeclF(_, _, _, v, _)               => v
      case FuncDeclF(fn, _)                        => fn.visibility
