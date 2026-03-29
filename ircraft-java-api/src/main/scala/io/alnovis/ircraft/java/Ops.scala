package io.alnovis.ircraft.java

import scala.jdk.CollectionConverters.*

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.expr.Block
import io.alnovis.ircraft.dialect.semantic.ops.*

/** Builders for semantic dialect operations. Java-friendly fluent API. */
object Ops:

  def iface(name: String): InterfaceBuilder                    = new InterfaceBuilder(name)
  def cls(name: String): ClassBuilder                          = new ClassBuilder(name)
  def method(name: String, returnType: TypeRef): MethodBuilder = new MethodBuilder(name, returnType)
  def file(packageName: String): FileBuilder                   = new FileBuilder(packageName)
  def enumClass(name: String): EnumClassBuilder                = new EnumClassBuilder(name)

  /** Pre-fill builder from existing InterfaceOp (for rebuild/enrich). */
  def rebuildIface(src: InterfaceOp): InterfaceBuilder =
    val b = new InterfaceBuilder(src.name)
    b.modifiersSet = src.modifiers
    b.typeParamsList = src.typeParams
    b.extendsList = src.extendsTypes
    b.methodsVec = src.methods
    b.nestedVec = src.nestedTypes
    b.jdoc = src.javadoc
    b.annotationsList = src.annotations
    b.attrs = src.attributes
    b

  /** Pre-fill builder from existing ClassOp (for rebuild/enrich). */
  def rebuildClass(src: ClassOp): ClassBuilder =
    val b = new ClassBuilder(src.name)
    b.modifiersSet = src.modifiers
    b.typeParamsList = src.typeParams
    b.superCls = src.superClass
    b.implTypes = src.implementsTypes
    b.fieldsVec = src.fields
    b.ctorsVec = src.constructors
    b.methodsVec = src.methods
    b.nestedVec = src.nestedTypes
    b.jdoc = src.javadoc
    b.annotationsList = src.annotations
    b.attrs = src.attributes
    b

  /** Create a public static final field with a string constant (value gets quoted). */
  def staticFinalField(name: String, fieldType: TypeRef, value: String): FieldDeclOp =
    FieldDeclOp(
      name,
      fieldType,
      modifiers = Set(Modifier.Public, Modifier.Static, Modifier.Final),
      defaultValue = Some("\"" + value + "\"")
    )

  /** Create a public static final field with a raw expression (e.g. "new ClassName()"). */
  def staticFinalFieldRaw(name: String, fieldType: TypeRef, rawExpr: String): FieldDeclOp =
    FieldDeclOp(
      name,
      fieldType,
      modifiers = Set(Modifier.Public, Modifier.Static, Modifier.Final),
      defaultValue = Some(rawExpr)
    )

  /** Create a private no-arg constructor. */
  def privateConstructor(): ConstructorOp =
    ConstructorOp(parameters = Nil, modifiers = Set(Modifier.Private))

// ── InterfaceOp Builder ────────────────────────────────────────────────────

class InterfaceBuilder private[java] (private val name: String):
  private[java] var modifiersSet: Set[Modifier]     = Set(Modifier.Public)
  private[java] var typeParamsList: List[TypeParam] = Nil
  private[java] var extendsList: List[TypeRef]      = Nil
  private[java] var methodsVec: Vector[MethodOp]    = Vector.empty
  private[java] var nestedVec: Vector[Operation]    = Vector.empty
  private[java] var jdoc: Option[String]            = None
  private[java] var annotationsList: List[String]   = Nil
  private[java] var attrs: AttributeMap             = AttributeMap.empty

  def modifiers(m: java.util.Set[Modifier]): InterfaceBuilder =
    modifiersSet = m.asScala.toSet; this

  def addTypeParam(tp: TypeParam): InterfaceBuilder =
    typeParamsList = typeParamsList :+ tp; this

  def addExtends(t: TypeRef): InterfaceBuilder =
    extendsList = extendsList :+ t; this

  def addMethod(m: MethodOp): InterfaceBuilder =
    methodsVec = methodsVec :+ m; this

  def addNestedType(op: Operation): InterfaceBuilder =
    nestedVec = nestedVec :+ op; this

  def javadoc(doc: String): InterfaceBuilder =
    jdoc = Some(doc); this

  def addAnnotation(a: String): InterfaceBuilder =
    annotationsList = annotationsList :+ a; this

  def attributes(a: AttributeMap): InterfaceBuilder =
    attrs = a; this

  def methods(ms: java.util.List[MethodOp]): InterfaceBuilder =
    methodsVec = ms.asScala.toVector; this

  def nestedTypes(ns: java.util.List[? <: Operation]): InterfaceBuilder =
    nestedVec = ns.asScala.toVector; this

  def build(): InterfaceOp = InterfaceOp(
    name,
    modifiersSet,
    typeParamsList,
    extendsList,
    methodsVec,
    nestedVec,
    jdoc,
    annotationsList,
    attrs
  )

// ── ClassOp Builder ────────────────────────────────────────────────────────

class ClassBuilder private[java] (private val name: String):
  private[java] var modifiersSet: Set[Modifier]     = Set(Modifier.Public)
  private[java] var typeParamsList: List[TypeParam] = Nil
  private[java] var superCls: Option[TypeRef]       = None
  private[java] var implTypes: List[TypeRef]        = Nil
  private[java] var fieldsVec: Vector[FieldDeclOp]  = Vector.empty
  private[java] var ctorsVec: Vector[ConstructorOp] = Vector.empty
  private[java] var methodsVec: Vector[MethodOp]    = Vector.empty
  private[java] var nestedVec: Vector[Operation]    = Vector.empty
  private[java] var jdoc: Option[String]            = None
  private[java] var annotationsList: List[String]   = Nil
  private[java] var attrs: AttributeMap             = AttributeMap.empty

  def modifiers(m: java.util.Set[Modifier]): ClassBuilder =
    modifiersSet = m.asScala.toSet; this

  def abstractClass(): ClassBuilder =
    modifiersSet = modifiersSet + Modifier.Abstract; this

  def addTypeParam(tp: TypeParam): ClassBuilder =
    typeParamsList = typeParamsList :+ tp; this

  def superClass(t: TypeRef): ClassBuilder =
    superCls = Some(t); this

  def addImplements(t: TypeRef): ClassBuilder =
    implTypes = implTypes :+ t; this

  def addField(f: FieldDeclOp): ClassBuilder =
    fieldsVec = fieldsVec :+ f; this

  def addConstructor(c: ConstructorOp): ClassBuilder =
    ctorsVec = ctorsVec :+ c; this

  def addMethod(m: MethodOp): ClassBuilder =
    methodsVec = methodsVec :+ m; this

  def addNestedType(op: Operation): ClassBuilder =
    nestedVec = nestedVec :+ op; this

  def javadoc(doc: String): ClassBuilder =
    jdoc = Some(doc); this

  def addAnnotation(a: String): ClassBuilder =
    annotationsList = annotationsList :+ a; this

  def attributes(a: AttributeMap): ClassBuilder =
    attrs = a; this

  def fields(fs: java.util.List[FieldDeclOp]): ClassBuilder =
    fieldsVec = fs.asScala.toVector; this

  def constructors(cs: java.util.List[ConstructorOp]): ClassBuilder =
    ctorsVec = cs.asScala.toVector; this

  def methods(ms: java.util.List[MethodOp]): ClassBuilder =
    methodsVec = ms.asScala.toVector; this

  def nestedTypes(ns: java.util.List[? <: Operation]): ClassBuilder =
    nestedVec = ns.asScala.toVector; this

  def build(): ClassOp = ClassOp(
    name,
    modifiersSet,
    typeParamsList,
    superCls,
    implTypes,
    fieldsVec,
    ctorsVec,
    methodsVec,
    nestedVec,
    jdoc,
    annotationsList,
    attrs
  )

// ── MethodOp Builder ──────────────────────────────────────────────────────

class MethodBuilder private[java] (private val name: String, private val returnType: TypeRef):
  private var params: List[Parameter]         = Nil
  private var modifiersSet: Set[Modifier]     = Set(Modifier.Public)
  private var typeParamsList: List[TypeParam] = Nil
  private var bodyOpt: Option[Block]          = None
  private var jdoc: Option[String]            = None
  private var annotationsList: List[String]   = Nil
  private var attrs: AttributeMap             = AttributeMap.empty

  def addParameter(p: Parameter): MethodBuilder =
    params = params :+ p; this
  def addParameter(name: String, paramType: TypeRef): MethodBuilder = addParameter(Parameter(name, paramType))

  def modifiers(m: java.util.Set[Modifier]): MethodBuilder =
    modifiersSet = m.asScala.toSet; this

  def addTypeParam(tp: TypeParam): MethodBuilder =
    typeParamsList = typeParamsList :+ tp; this

  def body(b: Block): MethodBuilder =
    bodyOpt = Some(b); this

  def javadoc(doc: String): MethodBuilder =
    jdoc = Some(doc); this

  def addAnnotation(a: String): MethodBuilder =
    annotationsList = annotationsList :+ a; this

  def attributes(a: AttributeMap): MethodBuilder =
    attrs = a; this

  // Convenience: common modifier combos
  def abstractPublic(): MethodBuilder =
    modifiersSet = Set(Modifier.Public, Modifier.Abstract); this

  def protectedAbstract(): MethodBuilder =
    modifiersSet = Set(Modifier.Protected, Modifier.Abstract); this

  def publicOverride(): MethodBuilder =
    modifiersSet = Set(Modifier.Public, Modifier.Override); this

  def build(): MethodOp = MethodOp(
    name,
    returnType,
    params,
    modifiersSet,
    typeParamsList,
    bodyOpt,
    jdoc,
    annotationsList,
    attrs
  )

// ── FileOp Builder ─────────────────────────────────────────────────────────

class FileBuilder private[java] (private val packageName: String):
  private var typesVec: Vector[Operation] = Vector.empty
  private var attrs: AttributeMap         = AttributeMap.empty

  def addType(op: Operation): FileBuilder =
    typesVec = typesVec :+ op; this

  def types(ops: java.util.List[? <: Operation]): FileBuilder =
    typesVec = ops.asScala.toVector; this

  def attributes(a: AttributeMap): FileBuilder =
    attrs = a; this

  def build(): FileOp = FileOp(packageName, typesVec, attrs)

// ── EnumClassOp Builder ────────────────────────────────────────────────────

class EnumClassBuilder private[java] (private val name: String):
  private var modifiersSet: Set[Modifier]          = Set(Modifier.Public)
  private var implTypes: List[TypeRef]             = Nil
  private var constantsVec: Vector[EnumConstantOp] = Vector.empty
  private var fieldsVec: Vector[FieldDeclOp]       = Vector.empty
  private var ctorsVec: Vector[ConstructorOp]      = Vector.empty
  private var methodsVec: Vector[MethodOp]         = Vector.empty
  private var jdoc: Option[String]                 = None
  private var annotationsList: List[String]        = Nil
  private var attrs: AttributeMap                  = AttributeMap.empty

  def modifiers(m: java.util.Set[Modifier]): EnumClassBuilder =
    modifiersSet = m.asScala.toSet; this

  def addImplements(t: TypeRef): EnumClassBuilder =
    implTypes = implTypes :+ t; this

  def addConstant(c: EnumConstantOp): EnumClassBuilder =
    constantsVec = constantsVec :+ c; this

  def constant(name: String, value: Int): EnumClassBuilder =
    constantsVec = constantsVec :+ EnumConstantOp(
      name,
      List(io.alnovis.ircraft.dialect.semantic.expr.Expression.Literal(value.toString, TypeRef.INT))
    )
    this

  def addField(f: FieldDeclOp): EnumClassBuilder =
    fieldsVec = fieldsVec :+ f; this

  def addConstructor(c: ConstructorOp): EnumClassBuilder =
    ctorsVec = ctorsVec :+ c; this

  def addMethod(m: MethodOp): EnumClassBuilder =
    methodsVec = methodsVec :+ m; this

  def javadoc(doc: String): EnumClassBuilder =
    jdoc = Some(doc); this

  def addAnnotation(a: String): EnumClassBuilder =
    annotationsList = annotationsList :+ a; this

  def attributes(a: AttributeMap): EnumClassBuilder =
    attrs = a; this

  def build(): EnumClassOp = EnumClassOp(
    name,
    modifiersSet,
    implTypes,
    constantsVec,
    fieldsVec,
    ctorsVec,
    methodsVec,
    jdoc,
    annotationsList,
    attrs
  )
