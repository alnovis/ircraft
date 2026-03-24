package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/**
  * Adds version conversion methods to interfaces and abstract classes.
  *
  * Adds to interface:
  *   - `asVersion(VersionContext)` → converts wrapper to another version
  *   - `getFieldsInaccessibleInVersion(String)` → lists fields missing in target version
  *
  * Adds to abstract class:
  *   - Concrete implementations delegating to VersionContext
  */
object VersionConversionPass extends Pass:

  val name: String        = "version-conversion"
  val description: String = "Adds asVersion() and getFieldsInaccessibleInVersion() methods"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case iface: InterfaceOp if iface.attributes.contains(ProtoAttributes.PresentInVersions) =>
        enrichInterface(iface)
      case cls: ClassOp if cls.isAbstract && cls.attributes.contains(ProtoAttributes.PresentInVersions) =>
        enrichAbstractClass(cls)
    PassResult(transformed)

  private def enrichInterface(iface: InterfaceOp): InterfaceOp =
    val methods = Vector(
      MethodOp(
        "asVersion",
        TypeRef.NamedType(iface.name),
        parameters = List(Parameter("targetContext", TypeRef.NamedType("VersionContext"))),
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        javadoc = Some("Converts this wrapper to another version using the given VersionContext.")
      ),
      MethodOp(
        "getFieldsInaccessibleInVersion",
        TypeRef.ListType(TypeRef.STRING),
        parameters = List(Parameter("targetVersionId", TypeRef.STRING)),
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        javadoc = Some("Returns field names that are not accessible in the target version.")
      )
    )
    InterfaceOp(
      name = iface.name,
      modifiers = iface.modifiers,
      typeParams = iface.typeParams,
      extendsTypes = iface.extendsTypes,
      methods = iface.methods ++ methods,
      nestedTypes = iface.nestedTypes,
      javadoc = iface.javadoc,
      annotations = iface.annotations,
      attributes = iface.attributes,
      span = iface.span
    )

  private def enrichAbstractClass(cls: ClassOp): ClassOp =
    val methods = Vector(
      // asVersion(VersionContext) → wraps toBytes + parseFromBytes on target context
      MethodOp(
        "asVersion",
        TypeRef.NamedType(
          cls.implementsTypes.headOption
            .map {
              case TypeRef.NamedType(n) => n
              case _                    => "Object"
            }
            .getOrElse("Object")
        ),
        parameters = List(Parameter("targetContext", TypeRef.NamedType("VersionContext"))),
        modifiers = Set(Modifier.Public, Modifier.Override),
        body = Some(
          Block.of(
            Statement.ReturnStmt(
              Some(
                Expression.MethodCall(
                  Some(Expression.Identifier("targetContext")),
                  s"wrap${cls.implementsTypes.headOption
                      .map {
                        case TypeRef.NamedType(n) => n
                        case _                    => "Unknown"
                      }
                      .getOrElse("Unknown")}",
                  List(Expression.MethodCall(None, "getTypedProto"))
                )
              )
            )
          )
        )
      ),
      // getFieldsInaccessibleInVersion — abstract, implemented by version impls
      MethodOp(
        "getFieldsInaccessibleInVersion",
        TypeRef.ListType(TypeRef.STRING),
        parameters = List(Parameter("targetVersionId", TypeRef.STRING)),
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )
    )

    ClassOp(
      name = cls.name,
      modifiers = cls.modifiers,
      typeParams = cls.typeParams,
      superClass = cls.superClass,
      implementsTypes = cls.implementsTypes,
      fields = cls.fields,
      constructors = cls.constructors,
      methods = cls.methods ++ methods,
      nestedTypes = cls.nestedTypes,
      javadoc = cls.javadoc,
      annotations = cls.annotations,
      attributes = cls.attributes,
      span = cls.span
    )
