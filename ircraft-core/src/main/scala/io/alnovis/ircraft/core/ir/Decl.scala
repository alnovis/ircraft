package io.alnovis.ircraft.core.ir

import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.SemanticF._

/** Smart constructors and extractors for Fix[SemanticF] (declaration trees).
  *
  * Provides ergonomic API: Decl.typeDecl(...) creates a Fix[SemanticF],
  * and Decl.TypeDecl.unapply enables pattern matching.
  */
object Decl {

  // ---- Smart constructors ----

  def typeDecl(
    name: String,
    kind: TypeKind,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[Fix[SemanticF]] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    typeParams: Vector[TypeParam] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix(TypeDeclF(name, kind, fields, functions, nested, supertypes, typeParams, visibility, annotations, meta))

  def enumDecl(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](EnumDeclF(name, variants, functions, supertypes, visibility, annotations, meta))

  def funcDecl(
    func: Func,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](FuncDeclF(func, meta))

  def aliasDecl(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](AliasDeclF(name, target, visibility, meta))

  def constDecl(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](ConstDeclF(name, constType, value, visibility, meta))

  // ---- Extractors for pattern matching ----

  object TypeDecl {
    def unapply(fix: Fix[SemanticF]): Option[TypeDeclF[Fix[SemanticF]]] = fix.unfix match {
      case td: TypeDeclF[Fix[SemanticF] @unchecked] => Some(td)
      case _ => None
    }
  }

  object EnumDecl {
    def unapply(fix: Fix[SemanticF]): Option[EnumDeclF[Fix[SemanticF]]] = fix.unfix match {
      case ed: EnumDeclF[Fix[SemanticF] @unchecked] => Some(ed)
      case _ => None
    }
  }

  object FuncDecl {
    def unapply(fix: Fix[SemanticF]): Option[FuncDeclF[Fix[SemanticF]]] = fix.unfix match {
      case fd: FuncDeclF[Fix[SemanticF] @unchecked] => Some(fd)
      case _ => None
    }
  }

  object AliasDecl {
    def unapply(fix: Fix[SemanticF]): Option[AliasDeclF[Fix[SemanticF]]] = fix.unfix match {
      case ad: AliasDeclF[Fix[SemanticF] @unchecked] => Some(ad)
      case _ => None
    }
  }

  object ConstDecl {
    def unapply(fix: Fix[SemanticF]): Option[ConstDeclF[Fix[SemanticF]]] = fix.unfix match {
      case cd: ConstDeclF[Fix[SemanticF] @unchecked] => Some(cd)
      case _ => None
    }
  }
}

// Supporting types remain unchanged below (TypeKind, Field, Func, etc.)

sealed abstract class TypeKind

object TypeKind {
  case object Product   extends TypeKind
  case object Sum       extends TypeKind
  case object Protocol  extends TypeKind
  case object Abstract  extends TypeKind
  case object Singleton extends TypeKind
}

case class Field(
  name: String,
  fieldType: TypeExpr,
  mutability: Mutability = Mutability.Immutable,
  defaultValue: Option[Expr] = None,
  visibility: Visibility = Visibility.Public,
  annotations: Vector[Annotation] = Vector.empty,
  meta: Meta = Meta.empty
)

sealed abstract class Mutability

object Mutability {
  case object Immutable extends Mutability
  case object Mutable   extends Mutability
}

case class Func(
  name: String,
  params: Vector[Param] = Vector.empty,
  returnType: TypeExpr = TypeExpr.VOID,
  body: Option[Body] = None,
  typeParams: Vector[TypeParam] = Vector.empty,
  visibility: Visibility = Visibility.Public,
  modifiers: Set[FuncModifier] = Set.empty,
  annotations: Vector[Annotation] = Vector.empty,
  meta: Meta = Meta.empty
)

case class Param(name: String, paramType: TypeExpr, defaultValue: Option[Expr] = None)

sealed abstract class FuncModifier

object FuncModifier {
  case object Override extends FuncModifier
  case object Static   extends FuncModifier
  case object Default  extends FuncModifier
  case object Suspend  extends FuncModifier
  case object Inline   extends FuncModifier
}

case class TypeParam(name: String, upperBounds: Vector[TypeExpr] = Vector.empty)

case class EnumVariant(
  name: String,
  args: Vector[Expr] = Vector.empty,
  fields: Vector[Field] = Vector.empty,
  meta: Meta = Meta.empty
)

sealed abstract class Visibility

object Visibility {
  case object Public         extends Visibility
  case object Private        extends Visibility
  case object Protected      extends Visibility
  case object Internal       extends Visibility
  case object PackagePrivate extends Visibility
}

case class Annotation(name: String, args: Map[String, Expr] = Map.empty)
