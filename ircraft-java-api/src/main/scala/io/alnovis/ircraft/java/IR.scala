package io.alnovis.ircraft.java

import scala.jdk.CollectionConverters.*

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*

/** IrModule, PassResult, collection accessors, and Modifier constants for Java consumers. */
object IR:

  // -- Modifier constants ---------------------------------------------------

  val PUBLIC: Modifier    = Modifier.Public
  val PRIVATE: Modifier   = Modifier.Private
  val PROTECTED: Modifier = Modifier.Protected
  val ABSTRACT: Modifier  = Modifier.Abstract
  val STATIC: Modifier    = Modifier.Static
  val FINAL: Modifier     = Modifier.Final
  val OVERRIDE: Modifier  = Modifier.Override
  val DEFAULT: Modifier   = Modifier.Default

  // -- IrModule creation ------------------------------------------------------

  def module(name: String, topLevel: java.util.List[? <: Operation]): IrModule =
    IrModule(name, topLevel.asScala.toVector)

  def module(name: String, topLevel: java.util.List[? <: Operation], attributes: AttributeMap): IrModule =
    IrModule(name, topLevel.asScala.toVector, attributes)

  // -- PassResult -----------------------------------------------------------

  def passResult(module: IrModule): PassResult = PassResult(module)

  def passResult(module: IrModule, diagnostics: java.util.List[DiagnosticMessage]): PassResult =
    PassResult(module, diagnostics.asScala.toList)

  // -- Collection accessors (Scala -> Java) ---------------------------------

  def topLevel(module: IrModule): java.util.List[Operation] =
    module.topLevel.asJava

  def types(file: FileOp): java.util.List[Operation] =
    file.types.asJava

  def methods(iface: InterfaceOp): java.util.List[MethodOp] =
    iface.methods.asJava

  def methods(cls: ClassOp): java.util.List[MethodOp] =
    cls.methods.asJava

  def fields(cls: ClassOp): java.util.List[FieldDeclOp] =
    cls.fields.asJava

  def constructors(cls: ClassOp): java.util.List[ConstructorOp] =
    cls.constructors.asJava

  def nestedTypes(iface: InterfaceOp): java.util.List[Operation] =
    iface.nestedTypes.asJava

  def nestedTypes(cls: ClassOp): java.util.List[Operation] =
    cls.nestedTypes.asJava

  def extendsTypes(iface: InterfaceOp): java.util.List[TypeRef] =
    iface.extendsTypes.asJava

  def implementsTypes(cls: ClassOp): java.util.List[TypeRef] =
    cls.implementsTypes.asJava

  def typeParams(iface: InterfaceOp): java.util.List[TypeParam] =
    iface.typeParams.asJava

  def typeParams(cls: ClassOp): java.util.List[TypeParam] =
    cls.typeParams.asJava

  def annotations(iface: InterfaceOp): java.util.List[String] =
    iface.annotations.asJava

  def annotations(cls: ClassOp): java.util.List[String] =
    cls.annotations.asJava

  def constants(e: EnumClassOp): java.util.List[EnumConstantOp] =
    e.constants.asJava

  // -- IrModule manipulation --------------------------------------------------

  def appendTopLevel(module: IrModule, extra: java.util.List[? <: Operation]): IrModule =
    IrModule(module.name, module.topLevel ++ extra.asScala, module.attributes, module.span)

  /**
    * Deep bottom-up transform: recursively transforms children via mapChildren first,
    * then applies f to the node with already-updated children.
    *
    * Java consumer returns the operation unchanged if no transformation is needed:
    * {{{
    * IR.transform(module, op -> {
    *     if (op instanceof InterfaceOp iface && condition)
    *         return enrichInterface(iface);
    *     return op;
    * });
    * }}}
    */
  def transform(module: IrModule, f: java.util.function.Function[Operation, Operation]): IrModule =
    def deep(op: Operation): Operation =
      val withTransformedChildren = op.mapChildren(deep)
      f.apply(withTransformedChildren)
    module.copy(topLevel = module.topLevel.map(deep))

  // -- Type-safe collect ----------------------------------------------------

  def collect[T <: Operation](module: IrModule, cls: Class[T]): java.util.List[T] =
    module.topLevel.flatMap(collectDeep(_, cls)).asJava

  private def collectDeep[T <: Operation](op: Operation, cls: Class[T]): Vector[T] =
    val self     = if cls.isInstance(op) then Vector(cls.cast(op)) else Vector.empty
    val children = op.regions.flatMap(_.operations.flatMap(collectDeep(_, cls)))
    self ++ children

  // -- Utility --------------------------------------------------------------

  def findApiPackage(module: IrModule): String =
    module.topLevel
      .collectFirst:
        case f: FileOp if f.types.exists(_.isInstanceOf[InterfaceOp]) => f.packageName
      .getOrElse("com.example.api")

  def javadoc(op: InterfaceOp): String | Null = op.javadoc.orNull
  def javadoc(op: ClassOp): String | Null     = op.javadoc.orNull
  def javadoc(op: MethodOp): String | Null    = op.javadoc.orNull

  def superClass(cls: ClassOp): TypeRef | Null = cls.superClass.orNull
