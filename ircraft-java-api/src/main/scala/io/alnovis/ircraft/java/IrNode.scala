package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

import java.util.{ Collections, List => JList, Optional }
import scala.jdk.CollectionConverters._

/**
  * Enumerates the kinds of IR declarations that an {@link IrNode} can represent.
  *
  * <p>Use this to dispatch on the variant of a node without casting. Each variant
  * corresponds to one of the SemanticF algebra constructors in the ircraft core.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrNode node = ...;
  * if (node.kind() == DeclKind.TypeDecl()) {
  *     TypeDeclView view = node.asTypeDecl().get();
  *     // work with fields, functions, nested types...
  * }
  * }}}
  *
  * @see [[IrNode#kind]]
  */
sealed abstract class DeclKind

/**
  * Companion object containing all {@link DeclKind} variants.
  *
  * <p>From Java, access these as {@code DeclKind.TypeDecl()}, {@code DeclKind.EnumDecl()}, etc.</p>
  */
object DeclKind {

  /** A type declaration (class, struct, message, record, etc.). */
  case object TypeDecl extends DeclKind

  /** An enumeration declaration with named variants. */
  case object EnumDecl extends DeclKind

  /** A standalone function declaration. */
  case object FuncDecl extends DeclKind

  /** A type alias (e.g., {@code type Foo = Bar}). */
  case object AliasDecl extends DeclKind

  /** A constant value declaration. */
  case object ConstDecl extends DeclKind
}

/**
  * Java-friendly wrapper over the ircraft recursive IR node ({@code Fix[SemanticF]}).
  *
  * <p>{@code IrNode} is the central building block of the ircraft intermediate representation.
  * Each node represents a single declaration -- a type, enum, function, alias, or constant.
  * Nodes are immutable; all mutation methods return new instances.</p>
  *
  * <p>To inspect the details of a node, use the typed accessor methods
  * ({@link #asTypeDecl}, {@link #asEnumDecl}, etc.) which return an {@code Optional}
  * containing a view object with the full declaration data.</p>
  *
  * <h3>Creating nodes from Java</h3>
  * {{{
  * import java.util.List;
  * import io.alnovis.ircraft.core.ir.*;
  *
  * // Simple type with no fields
  * IrNode empty = IrNode.typeDecl("MyMessage", TypeKind.Message());
  *
  * // Type with fields
  * IrNode withFields = IrNode.typeDecl("Person", TypeKind.Message(), List.of(
  *     Field.of("name", TypeExpr.string()),
  *     Field.of("age", TypeExpr.int32())
  * ));
  *
  * // Enum
  * IrNode color = IrNode.enumDecl("Color", List.of(
  *     EnumVariant.of("RED", 0),
  *     EnumVariant.of("GREEN", 1)
  * ));
  * }}}
  *
  * @see [[DeclKind]]          for the variant discriminator
  * @see [[TypeDeclView]]      for type declaration details
  * @see [[EnumDeclView]]      for enum declaration details
  * @see [[FuncDeclView]]      for function declaration details
  * @see [[AliasDeclView]]     for alias declaration details
  * @see [[ConstDeclView]]     for constant declaration details
  * @see [[IrCompilationUnit]] for grouping nodes into namespaced units
  */
final class IrNode private (private val fix: Fix[SemanticF]) {

  /**
    * Returns the name of this declaration.
    *
    * <p>Every IR declaration has a name (e.g., the type name, function name, constant name).</p>
    *
    * @return the declaration name, never {@code null}
    */
  def name: String = SemanticF.name(fix.unfix)

  /**
    * Returns the metadata attached to this declaration.
    *
    * @return the metadata; may be empty but never {@code null}
    * @see [[IrMeta]]
    */
  def meta: IrMeta = IrMeta.fromScala(SemanticF.meta(fix.unfix))

  /**
    * Returns a new {@code IrNode} with the given metadata replacing the current metadata.
    *
    * @param m the new metadata
    * @return a new node with updated metadata
    */
  def withMeta(m: IrMeta): IrNode =
    new IrNode(Fix[SemanticF](SemanticF.withMeta(fix.unfix, m.toScala)))

  /**
    * Returns the declaration kind of this node.
    *
    * <p>Use this to determine which typed accessor ({@link #asTypeDecl}, etc.) will
    * return a present value.</p>
    *
    * @return the {@link DeclKind} variant for this node
    */
  def kind: DeclKind = fix.unfix match {
    case _: TypeDeclF[?]  => DeclKind.TypeDecl
    case _: EnumDeclF[?]  => DeclKind.EnumDecl
    case _: FuncDeclF[?]  => DeclKind.FuncDecl
    case _: AliasDeclF[?] => DeclKind.AliasDecl
    case _: ConstDeclF[?] => DeclKind.ConstDecl
  }

  // -- Typed accessors --

  /**
    * Attempts to view this node as a type declaration.
    *
    * @return an {@code Optional} containing a {@link TypeDeclView} if this node is a type
    *         declaration, or {@code Optional.empty()} otherwise
    */
  def asTypeDecl: Optional[TypeDeclView] = fix.unfix match {
    case td: TypeDeclF[Fix[SemanticF] @unchecked] => Optional.of(TypeDeclView(td))
    case _                                        => Optional.empty()
  }

  /**
    * Attempts to view this node as an enum declaration.
    *
    * @return an {@code Optional} containing an {@link EnumDeclView} if this node is an enum
    *         declaration, or {@code Optional.empty()} otherwise
    */
  def asEnumDecl: Optional[EnumDeclView] = fix.unfix match {
    case ed: EnumDeclF[Fix[SemanticF] @unchecked] => Optional.of(EnumDeclView(ed))
    case _                                        => Optional.empty()
  }

  /**
    * Attempts to view this node as a function declaration.
    *
    * @return an {@code Optional} containing a {@link FuncDeclView} if this node is a function
    *         declaration, or {@code Optional.empty()} otherwise
    */
  def asFuncDecl: Optional[FuncDeclView] = fix.unfix match {
    case fd: FuncDeclF[Fix[SemanticF] @unchecked] => Optional.of(FuncDeclView(fd))
    case _                                        => Optional.empty()
  }

  /**
    * Attempts to view this node as a type alias declaration.
    *
    * @return an {@code Optional} containing an {@link AliasDeclView} if this node is an alias
    *         declaration, or {@code Optional.empty()} otherwise
    */
  def asAliasDecl: Optional[AliasDeclView] = fix.unfix match {
    case ad: AliasDeclF[Fix[SemanticF] @unchecked] => Optional.of(AliasDeclView(ad))
    case _                                         => Optional.empty()
  }

  /**
    * Attempts to view this node as a constant declaration.
    *
    * @return an {@code Optional} containing a {@link ConstDeclView} if this node is a constant
    *         declaration, or {@code Optional.empty()} otherwise
    */
  def asConstDecl: Optional[ConstDeclView] = fix.unfix match {
    case cd: ConstDeclF[Fix[SemanticF] @unchecked] => Optional.of(ConstDeclView(cd))
    case _                                         => Optional.empty()
  }

  /**
    * Returns the underlying Scala {@code Fix[SemanticF]} value.
    *
    * <p>Use this when bridging back to Scala-based ircraft APIs.</p>
    *
    * @return the Scala recursive IR node
    */
  def toScala: Fix[SemanticF] = fix

  override def toString: String = s"IrNode(${kind}, ${name})"

  override def equals(obj: Any): Boolean = obj match {
    case other: IrNode => fix == other.fix
    case _             => false
  }

  override def hashCode(): Int = fix.hashCode()
}

/**
  * Factory methods for creating {@link IrNode} instances.
  *
  * <p>Each factory method corresponds to one of the five IR declaration kinds.
  * Multiple overloads are provided: a full-parameter version for complete control,
  * and convenience versions with sensible defaults (public visibility, empty metadata,
  * no annotations).</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import java.util.List;
  * import io.alnovis.ircraft.core.ir.*;
  *
  * // Minimal type declaration
  * IrNode msg = IrNode.typeDecl("Request", TypeKind.Message());
  *
  * // Enum with variants
  * IrNode status = IrNode.enumDecl("Status", List.of(
  *     EnumVariant.of("OK", 0),
  *     EnumVariant.of("ERROR", 1)
  * ));
  *
  * // Function declaration
  * IrNode rpc = IrNode.funcDecl(Func.of("getUser", ...));
  *
  * // Type alias
  * IrNode alias = IrNode.aliasDecl("UserId", TypeExpr.string());
  *
  * // Constant
  * IrNode maxRetries = IrNode.constDecl("MAX_RETRIES", TypeExpr.int32(), Expr.intLit(3));
  * }}}
  *
  * @see [[IrNode]]
  * @see [[DeclKind]]
  */
object IrNode {

  /**
    * Wraps an existing Scala {@code Fix[SemanticF]} value as an {@code IrNode}.
    *
    * <p>This is the primary bridge from Scala-based ircraft APIs into the Java facade.</p>
    *
    * @param fix the Scala recursive IR node
    * @return a new {@code IrNode} wrapping the given value
    */
  def fromScala(fix: Fix[SemanticF]): IrNode = new IrNode(fix)

  /**
    * Creates a type declaration node with full control over all parameters.
    *
    * @param name        the type name
    * @param kind        the kind of type (e.g., Message, Struct, Class)
    * @param fields      the list of fields in this type
    * @param functions   the list of methods/functions in this type
    * @param nested      the list of nested type declarations
    * @param supertypes  the list of supertypes/interfaces this type extends
    * @param typeParams  the list of generic type parameters
    * @param visibility  the visibility modifier (Public, Private, etc.)
    * @param annotations the list of annotations on this type
    * @param meta        the metadata to attach
    * @return a new type declaration node
    */
  def typeDecl(
    name: String,
    kind: TypeKind,
    fields: JList[Field],
    functions: JList[Func],
    nested: JList[IrNode],
    supertypes: JList[TypeExpr],
    typeParams: JList[TypeParam],
    visibility: Visibility,
    annotations: JList[Annotation],
    meta: IrMeta
  ): IrNode = new IrNode(
    Decl.typeDecl(
      name,
      kind,
      fields.asScala.toVector,
      functions.asScala.toVector,
      nested.asScala.toVector.map(_.toScala),
      supertypes.asScala.toVector,
      typeParams.asScala.toVector,
      visibility,
      annotations.asScala.toVector,
      meta.toScala
    )
  )

  /**
    * Creates a type declaration node with minimal required parameters.
    *
    * <p>Uses defaults: no fields, no functions, no nested types, no supertypes,
    * no type parameters, public visibility, no annotations, empty metadata.</p>
    *
    * @param name the type name
    * @param kind the kind of type
    * @return a new type declaration node
    */
  def typeDecl(name: String, kind: TypeKind): IrNode =
    typeDecl(
      name,
      kind,
      JList.of(),
      JList.of(),
      JList.of(),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )

  /**
    * Creates a type declaration node with the given fields.
    *
    * <p>Uses defaults for all other parameters: no functions, no nested types,
    * public visibility, no annotations, empty metadata.</p>
    *
    * @param name   the type name
    * @param kind   the kind of type
    * @param fields the list of fields
    * @return a new type declaration node
    */
  def typeDecl(name: String, kind: TypeKind, fields: JList[Field]): IrNode =
    typeDecl(
      name,
      kind,
      fields,
      JList.of(),
      JList.of(),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )

  /**
    * Creates an enum declaration node with full control over all parameters.
    *
    * @param name        the enum name
    * @param variants    the list of enum variants
    * @param functions   the list of methods/functions in this enum
    * @param supertypes  the list of supertypes/interfaces this enum extends
    * @param visibility  the visibility modifier
    * @param annotations the list of annotations
    * @param meta        the metadata to attach
    * @return a new enum declaration node
    */
  def enumDecl(
    name: String,
    variants: JList[EnumVariant],
    functions: JList[Func],
    supertypes: JList[TypeExpr],
    visibility: Visibility,
    annotations: JList[Annotation],
    meta: IrMeta
  ): IrNode = new IrNode(
    Decl.enumDecl(
      name,
      variants.asScala.toVector,
      functions.asScala.toVector,
      supertypes.asScala.toVector,
      visibility,
      annotations.asScala.toVector,
      meta.toScala
    )
  )

  /**
    * Creates an enum declaration node with minimal required parameters.
    *
    * <p>Uses defaults: no functions, no supertypes, public visibility,
    * no annotations, empty metadata.</p>
    *
    * @param name     the enum name
    * @param variants the list of enum variants
    * @return a new enum declaration node
    */
  def enumDecl(name: String, variants: JList[EnumVariant]): IrNode =
    enumDecl(name, variants, JList.of(), JList.of(), Visibility.Public, JList.of(), IrMeta.empty)

  /**
    * Creates a function declaration node wrapping the given function.
    *
    * <p>Uses empty metadata.</p>
    *
    * @param func the function definition
    * @return a new function declaration node
    */
  def funcDecl(func: Func): IrNode =
    new IrNode(Decl.funcDecl(func))

  /**
    * Creates a function declaration node with the given function and metadata.
    *
    * @param func the function definition
    * @param meta the metadata to attach
    * @return a new function declaration node
    */
  def funcDecl(func: Func, meta: IrMeta): IrNode =
    new IrNode(Decl.funcDecl(func, meta.toScala))

  /**
    * Creates a type alias declaration node.
    *
    * <p>Uses defaults: public visibility, empty metadata.</p>
    *
    * @param name   the alias name
    * @param target the type expression this alias points to
    * @return a new alias declaration node
    */
  def aliasDecl(name: String, target: TypeExpr): IrNode =
    new IrNode(Decl.aliasDecl(name, target))

  /**
    * Creates a type alias declaration node with visibility and metadata.
    *
    * @param name       the alias name
    * @param target     the type expression this alias points to
    * @param visibility the visibility modifier
    * @param meta       the metadata to attach
    * @return a new alias declaration node
    */
  def aliasDecl(name: String, target: TypeExpr, visibility: Visibility, meta: IrMeta): IrNode =
    new IrNode(Decl.aliasDecl(name, target, visibility, meta.toScala))

  /**
    * Creates a constant declaration node.
    *
    * <p>Uses defaults: public visibility, empty metadata.</p>
    *
    * @param name      the constant name
    * @param constType the type of the constant
    * @param value     the constant's value expression
    * @return a new constant declaration node
    */
  def constDecl(name: String, constType: TypeExpr, value: Expr): IrNode =
    new IrNode(Decl.constDecl(name, constType, value))

  /**
    * Creates a constant declaration node with visibility and metadata.
    *
    * @param name       the constant name
    * @param constType  the type of the constant
    * @param value      the constant's value expression
    * @param visibility the visibility modifier
    * @param meta       the metadata to attach
    * @return a new constant declaration node
    */
  def constDecl(name: String, constType: TypeExpr, value: Expr, visibility: Visibility, meta: IrMeta): IrNode =
    new IrNode(Decl.constDecl(name, constType, value, visibility, meta.toScala))
}

// -- View records --

/**
  * Read-only view of a type declaration ({@link DeclKind#TypeDecl}).
  *
  * <p>Provides Java-friendly access to all fields of a type declaration, including
  * its fields, methods, nested types, supertypes, and type parameters. All
  * collection-returning methods return {@code java.util.List}.</p>
  *
  * <p>Obtain an instance via {@link IrNode#asTypeDecl}.</p>
  *
  * @see [[IrNode#asTypeDecl]]
  * @see [[IrVisitor#visitTypeDecl]]
  */
final case class TypeDeclView(
  private val td: TypeDeclF[Fix[SemanticF]]
) {

  /** Returns the type name. */
  def name: String = td.name

  /** Returns the kind of type (e.g., Message, Struct, Class). */
  def kind: TypeKind = td.kind

  /** Returns the list of fields declared in this type. */
  def fields: JList[Field] = td.fields.asJava

  /** Returns the list of methods/functions declared in this type. */
  def functions: JList[Func] = td.functions.asJava

  /** Returns the list of nested type declarations, each wrapped as an {@link IrNode}. */
  def nested: JList[IrNode] = td.nested.map(IrNode.fromScala).asJava

  /** Returns the list of supertypes/interfaces this type extends or implements. */
  def supertypes: JList[TypeExpr] = td.supertypes.asJava

  /** Returns the list of generic type parameters. */
  def typeParams: JList[TypeParam] = td.typeParams.asJava

  /** Returns the visibility modifier of this type. */
  def visibility: Visibility = td.visibility

  /** Returns the list of annotations on this type. */
  def annotations: JList[Annotation] = td.annotations.asJava

  /** Returns the metadata attached to this type declaration. */
  def meta: IrMeta = IrMeta.fromScala(td.meta)
}

/**
  * Read-only view of an enum declaration ({@link DeclKind#EnumDecl}).
  *
  * <p>Provides Java-friendly access to an enum's variants, methods, supertypes,
  * and annotations. All collection-returning methods return {@code java.util.List}.</p>
  *
  * <p>Obtain an instance via {@link IrNode#asEnumDecl}.</p>
  *
  * @see [[IrNode#asEnumDecl]]
  * @see [[IrVisitor#visitEnumDecl]]
  */
final case class EnumDeclView(
  private val ed: EnumDeclF[Fix[SemanticF]]
) {

  /** Returns the enum name. */
  def name: String = ed.name

  /** Returns the list of enum variants. */
  def variants: JList[EnumVariant] = ed.variants.asJava

  /** Returns the list of methods/functions declared in this enum. */
  def functions: JList[Func] = ed.functions.asJava

  /** Returns the list of supertypes/interfaces this enum extends or implements. */
  def supertypes: JList[TypeExpr] = ed.supertypes.asJava

  /** Returns the visibility modifier of this enum. */
  def visibility: Visibility = ed.visibility

  /** Returns the list of annotations on this enum. */
  def annotations: JList[Annotation] = ed.annotations.asJava

  /** Returns the metadata attached to this enum declaration. */
  def meta: IrMeta = IrMeta.fromScala(ed.meta)
}

/**
  * Read-only view of a function declaration ({@link DeclKind#FuncDecl}).
  *
  * <p>Wraps a standalone function (not a method on a type). Access the full
  * function definition via {@link #func}.</p>
  *
  * <p>Obtain an instance via {@link IrNode#asFuncDecl}.</p>
  *
  * @see [[IrNode#asFuncDecl]]
  * @see [[IrVisitor#visitFuncDecl]]
  */
final case class FuncDeclView(
  private val fd: FuncDeclF[Fix[SemanticF]]
) {

  /** Returns the function definition. */
  def func: Func = fd.func

  /** Returns the metadata attached to this function declaration. */
  def meta: IrMeta = IrMeta.fromScala(fd.meta)
}

/**
  * Read-only view of a type alias declaration ({@link DeclKind#AliasDecl}).
  *
  * <p>A type alias binds a new name to an existing type expression
  * (e.g., {@code type UserId = String}).</p>
  *
  * <p>Obtain an instance via {@link IrNode#asAliasDecl}.</p>
  *
  * @see [[IrNode#asAliasDecl]]
  * @see [[IrVisitor#visitAliasDecl]]
  */
final case class AliasDeclView(
  private val ad: AliasDeclF[Fix[SemanticF]]
) {

  /** Returns the alias name. */
  def name: String = ad.name

  /** Returns the target type expression that this alias refers to. */
  def target: TypeExpr = ad.target

  /** Returns the visibility modifier of this alias. */
  def visibility: Visibility = ad.visibility

  /** Returns the metadata attached to this alias declaration. */
  def meta: IrMeta = IrMeta.fromScala(ad.meta)
}

/**
  * Read-only view of a constant declaration ({@link DeclKind#ConstDecl}).
  *
  * <p>A constant declaration binds a name to a typed value expression
  * (e.g., {@code const MAX_RETRIES: int32 = 3}).</p>
  *
  * <p>Obtain an instance via {@link IrNode#asConstDecl}.</p>
  *
  * @see [[IrNode#asConstDecl]]
  * @see [[IrVisitor#visitConstDecl]]
  */
final case class ConstDeclView(
  private val cd: ConstDeclF[Fix[SemanticF]]
) {

  /** Returns the constant name. */
  def name: String = cd.name

  /** Returns the type of this constant. */
  def constType: TypeExpr = cd.constType

  /** Returns the value expression of this constant. */
  def value: Expr = cd.value

  /** Returns the visibility modifier of this constant. */
  def visibility: Visibility = cd.visibility

  /** Returns the metadata attached to this constant declaration. */
  def meta: IrMeta = IrMeta.fromScala(cd.meta)
}
