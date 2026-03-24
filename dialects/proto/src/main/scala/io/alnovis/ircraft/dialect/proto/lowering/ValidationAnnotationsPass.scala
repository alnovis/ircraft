package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/**
  * Adds validation annotations (@NotNull, @Valid) to interface getter methods.
  *
  *   - Message-type fields get @NotNull (non-nullable by default)
  *   - Optional fields do NOT get @NotNull
  *   - Repeated/map fields get @NotNull (empty collection, never null)
  *
  * Conditional: controlled by PassContext "generateValidationAnnotations".
  */
object ValidationAnnotationsPass extends Pass:

  val name: String        = "validation-annotations"
  val description: String = "Adds @NotNull and @Valid annotations to interface getters"

  override def isEnabled(context: PassContext): Boolean =
    context.getBool("generateValidationAnnotations")

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case iface: InterfaceOp if iface.attributes.contains(ProtoAttributes.PresentInVersions) =>
        val updatedMethods = iface.methods.map(addAnnotations)
        InterfaceOp(
          name = iface.name,
          modifiers = iface.modifiers,
          typeParams = iface.typeParams,
          extendsTypes = iface.extendsTypes,
          methods = updatedMethods,
          nestedTypes = iface.nestedTypes,
          javadoc = iface.javadoc,
          annotations = iface.annotations,
          attributes = iface.attributes,
          span = iface.span
        )
    PassResult(transformed)

  private def addAnnotations(m: MethodOp): MethodOp =
    if !m.name.startsWith("get") then return m

    val isOptional = m.attributes.getBool(ProtoAttributes.IsOptional).getOrElse(false)
    val isRepeated = m.attributes.getBool(ProtoAttributes.IsRepeated).getOrElse(false)
    val isMap      = m.attributes.getBool(ProtoAttributes.IsMap).getOrElse(false)

    val annotations = List.newBuilder[String]
    annotations ++= m.annotations

    if isRepeated || isMap then annotations += "NotNull"
    else if !isOptional then
      m.returnType match
        case TypeRef.NamedType(_) => annotations += "NotNull"
        case _                    => () // primitives don't need @NotNull

    val result = annotations.result().distinct
    if result == m.annotations then m
    else m.copy(annotations = result)
