package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/** Trait mixin instances for SemanticF. */
object SemanticInstances {

  implicit val semanticHasName: HasName[SemanticF] = new HasName[SemanticF] {
    def name[A](fa: SemanticF[A]): String = SemanticF.name(fa)
  }

  implicit val semanticHasMeta: HasMeta[SemanticF] = new HasMeta[SemanticF] {
    def meta[A](fa: SemanticF[A]): Meta = SemanticF.meta(fa)
    def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = SemanticF.withMeta(fa, m)
  }

  implicit val semanticHasFields: HasFields[SemanticF] = new HasFields[SemanticF] {
    def fields[A](fa: SemanticF[A]): Vector[Field] = fa match {
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) => fields
      case _ => Vector.empty
    }
  }

  implicit val semanticHasMethods: HasMethods[SemanticF] = new HasMethods[SemanticF] {
    def functions[A](fa: SemanticF[A]): Vector[Func] = fa match {
      case TypeDeclF(_, _, _, fns, _, _, _, _, _, _) => fns
      case EnumDeclF(_, _, fns, _, _, _, _)          => fns
      case FuncDeclF(fn, _)                          => Vector(fn)
      case _ => Vector.empty
    }
  }

  implicit val semanticHasNested: HasNested[SemanticF] = new HasNested[SemanticF] {
    def nested[A](fa: SemanticF[A]): Vector[A] = fa match {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested
      case _ => Vector.empty
    }
  }

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
