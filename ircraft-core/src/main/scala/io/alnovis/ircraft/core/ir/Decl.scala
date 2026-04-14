package io.alnovis.ircraft.core.ir

import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.SemanticF._

/**
  * Smart constructors and extractors for `Fix[SemanticF]` (declaration trees).
  *
  * Provides an ergonomic API for constructing and pattern-matching on the
  * fixpoint-wrapped semantic IR nodes without directly manipulating [[Fix]]
  * and [[SemanticF]] variants.
  *
  * '''Construction:'''
  * {{{
  * val cls = Decl.typeDecl("Person", TypeKind.Product, fields = Vector(
  *   Field("name", TypeExpr.STR),
  *   Field("age", TypeExpr.INT)
  * ))
  * }}}
  *
  * '''Pattern matching:'''
  * {{{
  * decl match {
  *   case Decl.TypeDecl(td) => println(td.name)
  *   case Decl.EnumDecl(ed) => println(ed.variants)
  *   case _                 => ()
  * }
  * }}}
  *
  * @see [[SemanticF]] for the underlying functor ADT
  * @see [[Fix]] for the fixpoint type wrapper
  */
object Decl {

  // ---- Smart constructors ----

  /**
    * Creates a type declaration node (class, struct, interface, etc.).
    *
    * @param name        the type name
    * @param kind        the kind of type (product, sum, protocol, abstract, singleton)
    * @param fields      the fields declared by this type
    * @param functions   the methods declared by this type
    * @param nested      nested declaration trees (inner classes, etc.)
    * @param supertypes  supertypes this type extends or implements
    * @param typeParams  generic type parameters
    * @param visibility  access modifier (defaults to [[Visibility.Public]])
    * @param annotations annotations attached to this type
    * @param meta        arbitrary metadata (defaults to [[Meta.empty]])
    * @return a `Fix[SemanticF]` wrapping a [[SemanticF.TypeDeclF]]
    */
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

  /**
    * Creates an enum declaration node.
    *
    * @param name        the enum name
    * @param variants    the enum variants (cases)
    * @param functions   methods declared on the enum
    * @param supertypes  supertypes this enum implements
    * @param visibility  access modifier (defaults to [[Visibility.Public]])
    * @param annotations annotations attached to this enum
    * @param meta        arbitrary metadata (defaults to [[Meta.empty]])
    * @return a `Fix[SemanticF]` wrapping a [[SemanticF.EnumDeclF]]
    */
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

  /**
    * Creates a top-level function declaration node.
    *
    * @param func the function definition
    * @param meta arbitrary metadata (defaults to [[Meta.empty]])
    * @return a `Fix[SemanticF]` wrapping a [[SemanticF.FuncDeclF]]
    */
  def funcDecl(
    func: Func,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](FuncDeclF(func, meta))

  /**
    * Creates a type alias declaration node.
    *
    * @param name       the alias name
    * @param target     the target type expression this alias refers to
    * @param visibility access modifier (defaults to [[Visibility.Public]])
    * @param meta       arbitrary metadata (defaults to [[Meta.empty]])
    * @return a `Fix[SemanticF]` wrapping a [[SemanticF.AliasDeclF]]
    */
  def aliasDecl(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](AliasDeclF(name, target, visibility, meta))

  /**
    * Creates a constant declaration node.
    *
    * @param name       the constant name
    * @param constType  the type of the constant
    * @param value      the constant's value expression
    * @param visibility access modifier (defaults to [[Visibility.Public]])
    * @param meta       arbitrary metadata (defaults to [[Meta.empty]])
    * @return a `Fix[SemanticF]` wrapping a [[SemanticF.ConstDeclF]]
    */
  def constDecl(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ): Fix[SemanticF] =
    Fix[SemanticF](ConstDeclF(name, constType, value, visibility, meta))

  // ---- Extractors for pattern matching ----

  /**
    * Extractor for [[SemanticF.TypeDeclF]] nodes inside `Fix[SemanticF]`.
    *
    * Enables pattern matching on fixpoint-wrapped type declarations:
    * {{{
    * fix match {
    *   case Decl.TypeDecl(td) => td.name
    * }
    * }}}
    */
  object TypeDecl {

    /**
      * Extracts a [[SemanticF.TypeDeclF]] from a `Fix[SemanticF]`, if present.
      *
      * @param fix the fixpoint-wrapped declaration to inspect
      * @return `Some(typeDeclF)` if the underlying node is a type declaration, `None` otherwise
      */
    def unapply(fix: Fix[SemanticF]): Option[TypeDeclF[Fix[SemanticF]]] = fix.unfix match {
      case td: TypeDeclF[Fix[SemanticF] @unchecked] => Some(td)
      case _                                        => None
    }
  }

  /**
    * Extractor for [[SemanticF.EnumDeclF]] nodes inside `Fix[SemanticF]`.
    *
    * Enables pattern matching on fixpoint-wrapped enum declarations:
    * {{{
    * fix match {
    *   case Decl.EnumDecl(ed) => ed.variants
    * }
    * }}}
    */
  object EnumDecl {

    /**
      * Extracts a [[SemanticF.EnumDeclF]] from a `Fix[SemanticF]`, if present.
      *
      * @param fix the fixpoint-wrapped declaration to inspect
      * @return `Some(enumDeclF)` if the underlying node is an enum declaration, `None` otherwise
      */
    def unapply(fix: Fix[SemanticF]): Option[EnumDeclF[Fix[SemanticF]]] = fix.unfix match {
      case ed: EnumDeclF[Fix[SemanticF] @unchecked] => Some(ed)
      case _                                        => None
    }
  }

  /**
    * Extractor for [[SemanticF.FuncDeclF]] nodes inside `Fix[SemanticF]`.
    *
    * Enables pattern matching on fixpoint-wrapped function declarations:
    * {{{
    * fix match {
    *   case Decl.FuncDecl(fd) => fd.func.name
    * }
    * }}}
    */
  object FuncDecl {

    /**
      * Extracts a [[SemanticF.FuncDeclF]] from a `Fix[SemanticF]`, if present.
      *
      * @param fix the fixpoint-wrapped declaration to inspect
      * @return `Some(funcDeclF)` if the underlying node is a function declaration, `None` otherwise
      */
    def unapply(fix: Fix[SemanticF]): Option[FuncDeclF[Fix[SemanticF]]] = fix.unfix match {
      case fd: FuncDeclF[Fix[SemanticF] @unchecked] => Some(fd)
      case _                                        => None
    }
  }

  /**
    * Extractor for [[SemanticF.AliasDeclF]] nodes inside `Fix[SemanticF]`.
    *
    * Enables pattern matching on fixpoint-wrapped alias declarations:
    * {{{
    * fix match {
    *   case Decl.AliasDecl(ad) => ad.target
    * }
    * }}}
    */
  object AliasDecl {

    /**
      * Extracts a [[SemanticF.AliasDeclF]] from a `Fix[SemanticF]`, if present.
      *
      * @param fix the fixpoint-wrapped declaration to inspect
      * @return `Some(aliasDeclF)` if the underlying node is an alias declaration, `None` otherwise
      */
    def unapply(fix: Fix[SemanticF]): Option[AliasDeclF[Fix[SemanticF]]] = fix.unfix match {
      case ad: AliasDeclF[Fix[SemanticF] @unchecked] => Some(ad)
      case _                                         => None
    }
  }

  /**
    * Extractor for [[SemanticF.ConstDeclF]] nodes inside `Fix[SemanticF]`.
    *
    * Enables pattern matching on fixpoint-wrapped constant declarations:
    * {{{
    * fix match {
    *   case Decl.ConstDecl(cd) => cd.value
    * }
    * }}}
    */
  object ConstDecl {

    /**
      * Extracts a [[SemanticF.ConstDeclF]] from a `Fix[SemanticF]`, if present.
      *
      * @param fix the fixpoint-wrapped declaration to inspect
      * @return `Some(constDeclF)` if the underlying node is a constant declaration, `None` otherwise
      */
    def unapply(fix: Fix[SemanticF]): Option[ConstDeclF[Fix[SemanticF]]] = fix.unfix match {
      case cd: ConstDeclF[Fix[SemanticF] @unchecked] => Some(cd)
      case _                                         => None
    }
  }
}

// Supporting types remain unchanged below (TypeKind, Field, Func, etc.)

/**
  * The structural kind of a type declaration.
  *
  * Determines how the type is represented in target languages:
  *  - [[TypeKind.Product]] -- a record/struct/class with named fields
  *  - [[TypeKind.Sum]] -- a sum type / sealed hierarchy
  *  - [[TypeKind.Protocol]] -- an interface / trait / protocol
  *  - [[TypeKind.Abstract]] -- an abstract class
  *  - [[TypeKind.Singleton]] -- a singleton object (no instances)
  *
  * @see [[Decl.typeDecl]] for creating type declarations with a kind
  */
sealed abstract class TypeKind

/** Companion containing the five standard type kinds. */
object TypeKind {

  /** A product type (record, struct, data class, message). */
  case object Product extends TypeKind

  /** A sum type (sealed trait, union, oneof). */
  case object Sum extends TypeKind

  /** A protocol type (interface, trait, protocol). */
  case object Protocol extends TypeKind

  /** An abstract class. */
  case object Abstract extends TypeKind

  /** A singleton object (no constructor, single instance). */
  case object Singleton extends TypeKind
}

/**
  * A field within a type declaration.
  *
  * Represents a named, typed member of a product or sum type. Fields may
  * carry a default value, mutability flag, visibility, annotations, and
  * arbitrary metadata.
  *
  * {{{
  * val nameField = Field("name", TypeExpr.STR)
  * val ageField  = Field("age", TypeExpr.INT, defaultValue = Some(Expr.Lit("0", TypeExpr.INT)))
  * }}}
  *
  * @param name         the field name
  * @param fieldType    the field's type expression
  * @param mutability   whether the field is mutable or immutable (defaults to [[Mutability.Immutable]])
  * @param defaultValue an optional default value expression
  * @param visibility   access modifier (defaults to [[Visibility.Public]])
  * @param annotations  annotations attached to this field
  * @param meta         arbitrary metadata
  * @see [[Func]] for method definitions
  * @see [[TypeExpr]] for the type expression system
  */
case class Field(
  name: String,
  fieldType: TypeExpr,
  mutability: Mutability = Mutability.Immutable,
  defaultValue: Option[Expr] = None,
  visibility: Visibility = Visibility.Public,
  annotations: Vector[Annotation] = Vector.empty,
  meta: Meta = Meta.empty
)

/**
  * Mutability flag for a [[Field]].
  *
  * @see [[Field]] for the primary consumer of this type
  */
sealed abstract class Mutability

/** Companion containing the two mutability variants. */
object Mutability {

  /** The field is immutable (val, final). */
  case object Immutable extends Mutability

  /** The field is mutable (var, non-final). */
  case object Mutable extends Mutability
}

/**
  * A function or method definition.
  *
  * Represents both standalone functions (via [[Decl.funcDecl]]) and methods
  * within type declarations. Contains the full signature (name, parameters,
  * return type, type parameters), modifiers, an optional body, and metadata.
  *
  * {{{
  * val getter = Func("getName", returnType = TypeExpr.STR, body = Some(
  *   Body.of(Stmt.Return(Some(Expr.Access(Expr.This, "name"))))
  * ))
  * }}}
  *
  * @param name        the function/method name
  * @param params      the parameter list
  * @param returnType  the return type (defaults to [[TypeExpr.VOID]])
  * @param body        an optional function body
  * @param typeParams  generic type parameters
  * @param visibility  access modifier (defaults to [[Visibility.Public]])
  * @param modifiers   function modifiers (override, static, default, etc.)
  * @param annotations annotations attached to this function
  * @param meta        arbitrary metadata
  * @see [[Param]] for parameter definitions
  * @see [[FuncModifier]] for available modifiers
  * @see [[Body]] for the body representation
  */
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

/**
  * A function parameter.
  *
  * @param name         the parameter name
  * @param paramType    the parameter's type expression
  * @param defaultValue an optional default value expression
  * @see [[Func]] for the function definition that contains parameters
  */
case class Param(name: String, paramType: TypeExpr, defaultValue: Option[Expr] = None)

/**
  * Modifiers applicable to a [[Func]] definition.
  *
  * These modifiers are language-neutral and map to appropriate constructs
  * in each target language during code generation.
  */
sealed abstract class FuncModifier

/** Companion containing the standard function modifier variants. */
object FuncModifier {

  /** The function overrides a supertype method. */
  case object Override extends FuncModifier

  /** The function is static (class-level, not instance-level). */
  case object Static extends FuncModifier

  /** The function is a default implementation (e.g., Java interface default method). */
  case object Default extends FuncModifier

  /** The function is suspendable (Kotlin coroutine, async). */
  case object Suspend extends FuncModifier

  /** The function should be inlined at call sites. */
  case object Inline extends FuncModifier
}

/**
  * A generic type parameter with optional upper bounds.
  *
  * {{{
  * val tParam = TypeParam("T", upperBounds = Vector(TypeExpr.Named("Comparable")))
  * }}}
  *
  * @param name        the type parameter name (e.g., "T", "A")
  * @param upperBounds upper-bound type constraints (extends/implements)
  */
case class TypeParam(name: String, upperBounds: Vector[TypeExpr] = Vector.empty)

/**
  * A variant (case) within an enum declaration.
  *
  * Enum variants may carry positional arguments (for simple enums with values),
  * named fields (for algebraic data type-style enums), and metadata.
  *
  * {{{
  * val variant = EnumVariant("Red", args = Vector(Expr.Lit("0xFF0000", TypeExpr.INT)))
  * }}}
  *
  * @param name   the variant name
  * @param args   positional constructor arguments
  * @param fields named fields (for ADT-style variants)
  * @param meta   arbitrary metadata
  * @see [[Decl.enumDecl]] for creating enum declarations
  */
case class EnumVariant(
  name: String,
  args: Vector[Expr] = Vector.empty,
  fields: Vector[Field] = Vector.empty,
  meta: Meta = Meta.empty
)

/**
  * Access visibility for declarations, fields, and functions.
  *
  * Maps to the appropriate access modifier in each target language
  * during code generation.
  *
  * @see [[Field.visibility]]
  * @see [[Func.visibility]]
  */
sealed abstract class Visibility

/** Companion containing the standard visibility variants. */
object Visibility {

  /** Visible everywhere. */
  case object Public extends Visibility

  /** Visible only within the declaring scope. */
  case object Private extends Visibility

  /** Visible within the declaring scope and subclasses. */
  case object Protected extends Visibility

  /** Visible within the same module/assembly (e.g., Kotlin `internal`). */
  case object Internal extends Visibility

  /** Visible within the same package (e.g., Java package-private). */
  case object PackagePrivate extends Visibility
}

/**
  * An annotation attached to a declaration, field, or function.
  *
  * Annotations carry a name and an optional map of named arguments.
  *
  * {{{
  * val deprecated = Annotation("Deprecated", Map("since" -> Expr.Lit("1.0", TypeExpr.STR)))
  * }}}
  *
  * @param name the annotation name (e.g., "Override", "Deprecated")
  * @param args named arguments for the annotation
  */
case class Annotation(name: String, args: Map[String, Expr] = Map.empty)
