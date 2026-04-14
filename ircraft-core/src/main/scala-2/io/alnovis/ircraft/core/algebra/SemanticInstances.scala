package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/**
  * Implicit structural typeclass instances for [[SemanticF]].
  *
  * This object provides [[HasName]], [[HasMeta]], [[HasFields]], [[HasMethods]],
  * [[HasNested]], and [[HasVisibility]] instances that allow generic passes to
  * extract structural information from [[SemanticF]] nodes without pattern matching
  * on the concrete variants.
  *
  * These instances are automatically available when this object is imported:
  * {{{
  * import io.alnovis.ircraft.core.algebra.SemanticInstances._
  * }}}
  *
  * @see [[CoproductInstances]] for auto-derived instances on coproducts
  * @see [[SemanticF]] for the dialect functor these instances operate on
  */
object SemanticInstances {

  /**
    * Implicit [[HasName]] instance for [[SemanticF]].
    *
    * Delegates to [[SemanticF.name]] to extract the declaration name
    * from any [[SemanticF]] variant.
    */
  implicit val semanticHasName: HasName[SemanticF] = new HasName[SemanticF] {
    def name[A](fa: SemanticF[A]): String = SemanticF.name(fa)
  }

  /**
    * Implicit [[HasMeta]] instance for [[SemanticF]].
    *
    * Delegates to [[SemanticF.meta]] and [[SemanticF.withMeta]] for
    * reading and updating metadata on any [[SemanticF]] variant.
    */
  implicit val semanticHasMeta: HasMeta[SemanticF] = new HasMeta[SemanticF] {
    def meta[A](fa: SemanticF[A]): Meta = SemanticF.meta(fa)
    def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = SemanticF.withMeta(fa, m)
  }

  /**
    * Implicit [[HasFields]] instance for [[SemanticF]].
    *
    * Returns the fields vector for [[TypeDeclF]] nodes; returns an
    * empty vector for all other variants.
    */
  implicit val semanticHasFields: HasFields[SemanticF] = new HasFields[SemanticF] {
    def fields[A](fa: SemanticF[A]): Vector[Field] = fa match {
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) => fields
      case _ => Vector.empty
    }
  }

  /**
    * Implicit [[HasMethods]] instance for [[SemanticF]].
    *
    * Returns functions from [[TypeDeclF]], [[EnumDeclF]], and [[FuncDeclF]];
    * returns an empty vector for other variants.
    */
  implicit val semanticHasMethods: HasMethods[SemanticF] = new HasMethods[SemanticF] {
    def functions[A](fa: SemanticF[A]): Vector[Func] = fa match {
      case TypeDeclF(_, _, _, fns, _, _, _, _, _, _) => fns
      case EnumDeclF(_, _, fns, _, _, _, _)          => fns
      case FuncDeclF(fn, _)                          => Vector(fn)
      case _ => Vector.empty
    }
  }

  /**
    * Implicit [[HasNested]] instance for [[SemanticF]].
    *
    * Returns the nested declarations vector for [[TypeDeclF]] nodes;
    * returns an empty vector for all other variants.
    */
  implicit val semanticHasNested: HasNested[SemanticF] = new HasNested[SemanticF] {
    def nested[A](fa: SemanticF[A]): Vector[A] = fa match {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested
      case _ => Vector.empty
    }
  }

  /**
    * Implicit [[HasVisibility]] instance for [[SemanticF]].
    *
    * Returns the visibility for all [[SemanticF]] variants. For [[FuncDeclF]],
    * the visibility is extracted from the inner [[Func]] definition.
    */
  implicit val semanticHasVisibility: HasVisibility[SemanticF] = new HasVisibility[SemanticF] {
    def visibility[A](fa: SemanticF[A]): Visibility = fa match {
      case TypeDeclF(_, _, _, _, _, _, _, v, _, _) => v
      case EnumDeclF(_, _, _, _, v, _, _)          => v
      case AliasDeclF(_, _, v, _)                  => v
      case ConstDeclF(_, _, _, v, _)               => v
      case FuncDeclF(fn, _)                        => fn.visibility
    }
  }
}
