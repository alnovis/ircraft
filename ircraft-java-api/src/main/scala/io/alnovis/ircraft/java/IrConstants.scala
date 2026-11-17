package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._

/**
  * Java-friendly access to Scala singleton objects from the ircraft core IR.
  *
  * <p>Scala 3 sealed class members (case objects) like {@code TypeKind.Protocol}
  * are not directly importable from Java source code due to {@code $} encoding
  * in class names. This class exposes them as plain static fields.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import io.alnovis.ircraft.java.IrConstants;
  *
  * TypeKind protocol = IrConstants.TYPE_PROTOCOL;
  * Visibility pub = IrConstants.VIS_PUBLIC;
  * }}}
  */
object IrConstants {

  // --- TypeKind ---

  /** Class/struct/record with named fields. */
  val TYPE_PRODUCT: TypeKind = TypeKind.Product

  /** Sum type / sealed hierarchy. */
  val TYPE_SUM: TypeKind = TypeKind.Sum

  /** Interface / trait / protocol. */
  val TYPE_PROTOCOL: TypeKind = TypeKind.Protocol

  /** Abstract class. */
  val TYPE_ABSTRACT: TypeKind = TypeKind.Abstract

  /** Singleton object (no instances). */
  val TYPE_SINGLETON: TypeKind = TypeKind.Singleton

  // --- Visibility ---

  val VIS_PUBLIC: Visibility = Visibility.Public
  val VIS_PRIVATE: Visibility = Visibility.Private
  val VIS_PROTECTED: Visibility = Visibility.Protected
  val VIS_INTERNAL: Visibility = Visibility.Internal
  val VIS_PACKAGE_PRIVATE: Visibility = Visibility.PackagePrivate

  // --- Mutability ---

  val MUT_IMMUTABLE: Mutability = Mutability.Immutable
  val MUT_MUTABLE: Mutability = Mutability.Mutable

  // --- FuncModifier ---

  val MOD_OVERRIDE: FuncModifier = FuncModifier.Override
  val MOD_STATIC: FuncModifier = FuncModifier.Static
  val MOD_DEFAULT: FuncModifier = FuncModifier.Default
  val MOD_SUSPEND: FuncModifier = FuncModifier.Suspend
  val MOD_INLINE: FuncModifier = FuncModifier.Inline

  // --- Expr singletons ---

  /** The `this` expression. */
  val EXPR_THIS: Expr = Expr.This

  /** The `super` expression. */
  val EXPR_SUPER: Expr = Expr.Super

  /** The `null` literal. */
  val EXPR_NULL: Expr = Expr.Null

  // --- Emit conventions ---

  /**
    * Meta key for marking supertype indices as `implements` (not `extends`).
    * Set this on a TypeDecl to control Java emit behavior.
    * Value: Set of Int indices into the supertypes vector.
    */
  val IMPLEMENTS_INDICES_KEY: Meta.Key[scala.collection.immutable.Set[Int]] =
    io.alnovis.ircraft.emit.BaseEmitter.ImplementsIndicesKey

  /**
    * Helper: set implementsIndices on IrMeta. Marks ALL supertypes as `implements`.
    */
  def setAllImplements(meta: IrMeta, count: Int): IrMeta = {
    val indices = (0 until count).toSet
    meta.set(IMPLEMENTS_INDICES_KEY, indices)
  }

  /** Meta key to mark a Func as a constructor (no return type emitted). */
  val CONSTRUCTOR_KEY: Meta.Key[Boolean] =
    io.alnovis.ircraft.emit.BaseEmitter.ConstructorKey

  /** Meta key to add class-level fields to an enum declaration. */
  val ENUM_FIELDS_KEY: Meta.Key[scala.collection.immutable.Vector[Field]] =
    io.alnovis.ircraft.emit.BaseEmitter.EnumFieldsKey

  /** Set constructor flag on a Func's meta (raw Meta). */
  def markConstructor(rawMeta: AnyRef): AnyRef = {
    val m = rawMeta.asInstanceOf[Meta]
    m.set(CONSTRUCTOR_KEY, true).asInstanceOf[AnyRef]
  }

  /** Set enum fields on meta (raw Meta for EnumDeclF). */
  def setEnumFields(meta: IrMeta, fields: java.util.List[Field]): IrMeta = {
    import scala.jdk.CollectionConverters._
    meta.set(ENUM_FIELDS_KEY, fields.asScala.toVector)
  }

  // --- Meta.Key factory and IrMeta helpers ---

  /** Create a typed metadata key. Returns Object because Java can't reference Meta.Key. */
  def metaKey[A](name: String): Meta.Key[A] = Meta.Key[A](name)

  /**
    * Set a metadata value using an untyped key (Object).
    * This bridges the gap where Java cannot reference Meta.Key directly.
    */
  def setMeta(meta: IrMeta, key: AnyRef, value: AnyRef): IrMeta =
    meta.set(key.asInstanceOf[Meta.Key[AnyRef]], value)

  /**
    * Get a metadata value using an untyped key (Object).
    * Returns Optional (Java).
    */
  def getMeta(meta: IrMeta, key: AnyRef): java.util.Optional[AnyRef] =
    meta.get(key.asInstanceOf[Meta.Key[AnyRef]])

  /**
    * Set a metadata value on raw Meta (for use in IR constructors).
    * Returns raw Meta (scala Map).
    */
  def setRawMeta(meta: AnyRef, key: AnyRef, value: AnyRef): AnyRef = {
    val m = meta.asInstanceOf[Meta]
    val k = key.asInstanceOf[Meta.Key[AnyRef]]
    m.set(k, value).asInstanceOf[AnyRef]
  }

  /** Get empty raw Meta for IR constructors. */
  def emptyRawMeta: AnyRef = Meta.empty.asInstanceOf[AnyRef]
}
