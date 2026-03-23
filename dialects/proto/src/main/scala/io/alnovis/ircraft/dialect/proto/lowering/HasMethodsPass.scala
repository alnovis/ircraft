package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/** Adds `hasXxx()` and `supportsXxx()` methods to interfaces and abstract classes.
  *
  *   - `hasXxx()` for optional fields → delegates to proto `hasXxx()`
  *   - `supportsXxx()` for fields not present in all versions → returns version check
  *
  * Reads: ProtoAttributes.IsOptional, ProtoAttributes.FieldPresentInVersions, ProtoAttributes.SchemaVersions
  */
object HasMethodsPass extends Pass:

  val name: String        = "has-methods"
  val description: String = "Adds has/supports methods for optional and version-specific fields"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case iface: InterfaceOp => enrichInterface(iface)
      case cls: ClassOp if cls.isAbstract => enrichAbstractClass(cls)
    PassResult(transformed)

  private def enrichInterface(iface: InterfaceOp): InterfaceOp =
    val schemaVersions = iface.attributes.getStringList(ProtoAttributes.SchemaVersions).getOrElse(Nil)
    val additionalMethods = iface.methods.flatMap(m => hasMethodsForInterface(m, schemaVersions))
    if additionalMethods.isEmpty then iface
    else
      InterfaceOp(
        name = iface.name,
        modifiers = iface.modifiers,
        typeParams = iface.typeParams,
        extendsTypes = iface.extendsTypes,
        methods = iface.methods ++ additionalMethods,
        nestedTypes = iface.nestedTypes,
        javadoc = iface.javadoc,
        annotations = iface.annotations,
        attributes = iface.attributes,
        span = iface.span
      )

  private def enrichAbstractClass(cls: ClassOp): ClassOp =
    val schemaVersions = cls.attributes.getStringList(ProtoAttributes.SchemaVersions).getOrElse(Nil)
    val additionalMethods = cls.methods.flatMap(m => hasMethodsForAbstract(m, schemaVersions))
    if additionalMethods.isEmpty then cls
    else
      ClassOp(
        name = cls.name,
        modifiers = cls.modifiers,
        typeParams = cls.typeParams,
        superClass = cls.superClass,
        implementsTypes = cls.implementsTypes,
        fields = cls.fields,
        constructors = cls.constructors,
        methods = cls.methods ++ additionalMethods,
        nestedTypes = cls.nestedTypes,
        javadoc = cls.javadoc,
        annotations = cls.annotations,
        attributes = cls.attributes,
        span = cls.span
      )

  private def hasMethodsForInterface(m: MethodOp, schemaVersions: List[String]): Vector[MethodOp] =
    if !m.name.startsWith("get") then return Vector.empty
    val fieldName = m.name.drop(3)
    val methods   = Vector.newBuilder[MethodOp]

    // hasXxx for optional fields
    val isOptional = m.attributes.getBool(ProtoAttributes.IsOptional).getOrElse(false)
    if isOptional then
      methods += MethodOp(
        s"has$fieldName",
        TypeRef.BOOL,
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        javadoc = Some(s"Returns true if ${fieldName.toLowerCase} is set.")
      )

    // supportsXxx for version-specific fields
    val fieldVersions = m.attributes.getStringList(ProtoAttributes.FieldPresentInVersions).getOrElse(Nil)
    if fieldVersions.nonEmpty && fieldVersions.size < schemaVersions.size then
      methods += MethodOp(
        s"supports$fieldName",
        TypeRef.BOOL,
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        javadoc = Some(s"Returns true if this version supports the ${fieldName.toLowerCase} field.")
      )

    methods.result()

  private def hasMethodsForAbstract(m: MethodOp, schemaVersions: List[String]): Vector[MethodOp] =
    if !m.name.startsWith("extract") then return Vector.empty
    val fieldName = m.name.drop(7)
    val methods   = Vector.newBuilder[MethodOp]

    val isOptional = m.attributes.getBool(ProtoAttributes.IsOptional).getOrElse(false)
    if isOptional then
      methods += MethodOp(
        s"extractHas$fieldName",
        TypeRef.BOOL,
        modifiers = Set(Modifier.Protected, Modifier.Abstract)
      )
      methods += MethodOp(
        s"has$fieldName",
        TypeRef.BOOL,
        modifiers = Set(Modifier.Public, Modifier.Override),
        body = Some(
          io.alnovis.ircraft.dialect.semantic.expr.Block.of(
            io.alnovis.ircraft.dialect.semantic.expr.Statement.ReturnStmt(
              Some(io.alnovis.ircraft.dialect.semantic.expr.Expression.MethodCall(None, s"extractHas$fieldName"))
            )
          )
        )
      )

    val fieldVersions = m.attributes.getStringList(ProtoAttributes.FieldPresentInVersions).getOrElse(Nil)
    if fieldVersions.nonEmpty && fieldVersions.size < schemaVersions.size then
      methods += MethodOp(
        s"supports$fieldName",
        TypeRef.BOOL,
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )

    methods.result()
