package io.alnovis.ircraft.core.ir

sealed trait Decl:
  def name: String
  def meta: Meta
  def withMeta(meta: Meta): Decl

object Decl:
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
  ) extends Decl:
    def withMeta(m: Meta): Decl = copy(meta = m)

  case class EnumDecl(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends Decl:
    def withMeta(m: Meta): Decl = copy(meta = m)

  case class FuncDecl(
    func: Func,
    meta: Meta = Meta.empty
  ) extends Decl:
    def name: String = func.name
    def withMeta(m: Meta): Decl = copy(meta = m)

  case class AliasDecl(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends Decl:
    def withMeta(m: Meta): Decl = copy(meta = m)

  case class ConstDecl(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends Decl:
    def withMeta(m: Meta): Decl = copy(meta = m)

enum TypeKind:
  case Product
  case Sum
  case Protocol
  case Abstract
  case Singleton

case class Field(
  name: String,
  fieldType: TypeExpr,
  mutability: Mutability = Mutability.Immutable,
  defaultValue: Option[Expr] = None,
  visibility: Visibility = Visibility.Public,
  annotations: Vector[Annotation] = Vector.empty,
  meta: Meta = Meta.empty
)

enum Mutability:
  case Immutable, Mutable

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

enum FuncModifier:
  case Override, Static, Default, Suspend, Inline

case class TypeParam(name: String, upperBounds: Vector[TypeExpr] = Vector.empty)

case class EnumVariant(
  name: String,
  args: Vector[Expr] = Vector.empty,
  fields: Vector[Field] = Vector.empty,
  meta: Meta = Meta.empty
)

enum Visibility:
  case Public, Private, Protected, Internal, PackagePrivate

case class Annotation(name: String, args: Map[String, Expr] = Map.empty)
