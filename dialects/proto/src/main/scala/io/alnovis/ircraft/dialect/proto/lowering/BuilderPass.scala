package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Generates Builder pattern for message wrappers (conditional: generateBuilders=true).
  *
  * Adds to interface:
  *   - Nested `Builder` interface with `setXxx()`, `clearXxx()`, `build()` methods
  *   - `toBuilder()` abstract method
  *
  * Adds to abstract class:
  *   - Nested `AbstractBuilder<PROTO>` with abstract `doSetXxx()`, `doClearXxx()`, `doBuild()` methods
  *   - Concrete setter/clear methods delegating to abstract
  *
  * Adds to impl class:
  *   - `toBuilder()` implementation
  */
object BuilderPass extends Pass:

  val name: String        = "builder-pattern"
  val description: String = "Generates Builder interfaces and implementations"

  override def isEnabled(context: PassContext): Boolean =
    context.getBool("generateBuilders")

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case iface: InterfaceOp if iface.attributes.contains(ProtoAttributes.PresentInVersions) =>
        enrichInterface(iface)
      case cls: ClassOp if cls.isAbstract && cls.attributes.contains(ProtoAttributes.PresentInVersions) =>
        enrichAbstractClass(cls)
      case cls: ClassOp if !cls.isAbstract && cls.attributes.contains(ProtoAttributes.ImplVersion) =>
        enrichImplClass(cls)
    PassResult(transformed)

  // ── Interface: nested Builder interface + toBuilder() ──────────────────

  private def enrichInterface(iface: InterfaceOp): InterfaceOp =
    val fieldGetters = iface.methods.filter(_.name.startsWith("get"))

    val setters = fieldGetters.map: m =>
      val fieldName = m.name.drop(3) // getAmount → Amount
      MethodOp(
        s"set$fieldName",
        TypeRef.NamedType("Builder"),
        parameters = List(Parameter(decapitalize(fieldName), m.returnType)),
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )

    val clearMethods = fieldGetters.map: m =>
      val fieldName = m.name.drop(3)
      MethodOp(
        s"clear$fieldName",
        TypeRef.NamedType("Builder"),
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )

    val buildMethod = MethodOp(
      "build",
      TypeRef.NamedType(iface.name),
      modifiers = Set(Modifier.Public, Modifier.Abstract)
    )

    val builderInterface = InterfaceOp(
      name = "Builder",
      methods = (setters ++ clearMethods :+ buildMethod).toVector
    )

    val toBuilder = MethodOp(
      "toBuilder",
      TypeRef.NamedType("Builder"),
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      javadoc = Some("Creates a builder initialized with this wrapper's values.")
    )

    InterfaceOp(
      name = iface.name,
      modifiers = iface.modifiers,
      typeParams = iface.typeParams,
      extendsTypes = iface.extendsTypes,
      methods = iface.methods :+ toBuilder,
      nestedTypes = iface.nestedTypes :+ builderInterface,
      javadoc = iface.javadoc,
      annotations = iface.annotations,
      attributes = iface.attributes,
      span = iface.span
    )

  // ── Abstract class: nested AbstractBuilder ─────────────────────────────

  private def enrichAbstractClass(cls: ClassOp): ClassOp =
    val extractMethods = cls.methods.filter(_.name.startsWith("extract"))

    val doSetMethods = extractMethods.map: m =>
      val fieldName = m.name.drop(7) // extractAmount → Amount
      MethodOp(
        s"doSet$fieldName",
        TypeRef.VOID,
        parameters = List(Parameter(decapitalize(fieldName), m.returnType)),
        modifiers = Set(Modifier.Protected, Modifier.Abstract)
      )

    val doClearMethods = extractMethods.map: m =>
      val fieldName = m.name.drop(7)
      MethodOp(
        s"doClear$fieldName",
        TypeRef.VOID,
        modifiers = Set(Modifier.Protected, Modifier.Abstract)
      )

    val doBuild = MethodOp(
      "doBuild",
      TypeRef.NamedType(cls.implementsTypes.headOption.map {
        case TypeRef.NamedType(n) => n
        case _                    => "Object"
      }.getOrElse("Object")),
      modifiers = Set(Modifier.Protected, Modifier.Abstract)
    )

    val concreteSetters = extractMethods.map: m =>
      val fieldName = m.name.drop(7)
      MethodOp(
        s"set$fieldName",
        TypeRef.NamedType("AbstractBuilder"),
        parameters = List(Parameter(decapitalize(fieldName), m.returnType)),
        modifiers = Set(Modifier.Public),
        body = Some(Block.of(
          Statement.ExpressionStmt(Expression.MethodCall(None, s"doSet$fieldName", List(Expression.Identifier(decapitalize(fieldName))))),
          Statement.ReturnStmt(Some(Expression.ThisRef))
        ))
      )

    val buildImpl = MethodOp(
      "build",
      TypeRef.NamedType(cls.implementsTypes.headOption.map {
        case TypeRef.NamedType(n) => n
        case _                    => "Object"
      }.getOrElse("Object")),
      modifiers = Set(Modifier.Public),
      body = Some(Block.of(Statement.ReturnStmt(Some(Expression.MethodCall(None, "doBuild")))))
    )

    val abstractBuilder = ClassOp(
      name = "AbstractBuilder",
      modifiers = Set(Modifier.Public, Modifier.Abstract, Modifier.Static),
      methods = (doSetMethods ++ doClearMethods :+ doBuild) ++ concreteSetters :+ buildImpl
    )

    ClassOp(
      name = cls.name,
      modifiers = cls.modifiers,
      typeParams = cls.typeParams,
      superClass = cls.superClass,
      implementsTypes = cls.implementsTypes,
      fields = cls.fields,
      constructors = cls.constructors,
      methods = cls.methods,
      nestedTypes = cls.nestedTypes :+ abstractBuilder,
      javadoc = cls.javadoc,
      annotations = cls.annotations,
      attributes = cls.attributes,
      span = cls.span
    )

  // ── Impl class: toBuilder() ────────────────────────────────────────────

  private def enrichImplClass(cls: ClassOp): ClassOp =
    val toBuilder = MethodOp(
      "toBuilder",
      TypeRef.NamedType("Builder"),
      modifiers = Set(Modifier.Public, Modifier.Override),
      javadoc = Some("Creates a builder from this wrapper's proto."),
      body = Some(Block.of(
        Statement.ThrowStmt(Expression.NewInstance(
          TypeRef.NamedType("UnsupportedOperationException"),
          List(Expression.Literal("\"Builder not yet implemented for this version\"", TypeRef.STRING))
        ))
      ))
    )

    ClassOp(
      name = cls.name,
      modifiers = cls.modifiers,
      typeParams = cls.typeParams,
      superClass = cls.superClass,
      implementsTypes = cls.implementsTypes,
      fields = cls.fields,
      constructors = cls.constructors,
      methods = cls.methods :+ toBuilder,
      nestedTypes = cls.nestedTypes,
      javadoc = cls.javadoc,
      annotations = cls.annotations,
      attributes = cls.attributes,
      span = cls.span
    )

  private def decapitalize(s: String): String =
    if s.isEmpty then s
    else s.head.toLower +: s.tail
