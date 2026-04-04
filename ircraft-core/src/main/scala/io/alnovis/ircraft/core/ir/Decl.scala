package io.alnovis.ircraft.core.ir

sealed trait Decl {
  def name: String
  def meta: Meta
  def withMeta(meta: Meta): Decl
}

object Decl {

  case class TypeDecl(
    name: String,
    kind: TypeKind,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[Decl] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    typeParams: Vector[TypeParam] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends Decl {
    def withMeta(m: Meta): Decl = copy(meta = m)
  }

  case class EnumDecl(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends Decl {
    def withMeta(m: Meta): Decl = copy(meta = m)
  }

  case class FuncDecl(
    func: Func,
    meta: Meta = Meta.empty
  ) extends Decl {
    def name: String            = func.name
    def withMeta(m: Meta): Decl = copy(meta = m)
  }

  case class AliasDecl(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends Decl {
    def withMeta(m: Meta): Decl = copy(meta = m)
  }

  case class ConstDecl(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends Decl {
    def withMeta(m: Meta): Decl = copy(meta = m)
  }
}

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
