package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/**
  * Generates the ProtoWrapper base interface that all message interfaces extend.
  *
  * Produces:
  * {{{
  * public interface ProtoWrapper {
  *   Message getTypedProto();
  *   String getWrapperVersionId();
  *   byte[] toBytes();
  * }
  * }}}
  *
  * Also updates all existing message InterfaceOps to extend ProtoWrapper.
  */
object ProtoWrapperPass extends Pass:

  val name: String        = "proto-wrapper-interface"
  val description: String = "Generates ProtoWrapper base interface and updates message interfaces to extend it"

  def run(module: Module, context: PassContext): PassResult =
    // Find API package
    val apiPackage = module.topLevel
      .collectFirst:
        case f: FileOp if f.types.exists(_.isInstanceOf[InterfaceOp]) => f.packageName
      .getOrElse("com.example.api")

    // Generate ProtoWrapper interface
    val protoWrapperInterface = InterfaceOp(
      name = "ProtoWrapper",
      methods = Vector(
        MethodOp(
          "getTypedProto",
          TypeRef.NamedType("com.google.protobuf.Message"),
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some("Returns the underlying proto message.")
        ),
        MethodOp(
          "getWrapperVersionId",
          TypeRef.STRING,
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some("Returns the version identifier (e.g., \"v1\", \"v2\").")
        ),
        MethodOp(
          "toBytes",
          TypeRef.BYTES,
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some("Serializes the underlying proto to bytes.")
        )
      ),
      javadoc = Some("Base interface for all proto wrapper types.")
    )

    val wrapperFile = FileOp(apiPackage, Vector(protoWrapperInterface))

    // Update existing message interfaces to extend ProtoWrapper
    val updatedTopLevel = module.topLevel.map:
      case f: FileOp =>
        val updatedTypes = f.types.map:
          case iface: InterfaceOp
              if iface.name != "ProtoWrapper" && iface.attributes.contains(ProtoAttributes.PresentInVersions) =>
            InterfaceOp(
              name = iface.name,
              modifiers = iface.modifiers,
              typeParams = iface.typeParams,
              extendsTypes = iface.extendsTypes :+ TypeRef.NamedType("ProtoWrapper"),
              methods = iface.methods,
              nestedTypes = iface.nestedTypes,
              javadoc = iface.javadoc,
              annotations = iface.annotations,
              attributes = iface.attributes,
              span = iface.span
            )
          case other => other
        FileOp(f.packageName, updatedTypes.toVector, f.attributes, f.span)
      case other => other

    PassResult(module.copy(topLevel = updatedTopLevel :+ wrapperFile))
