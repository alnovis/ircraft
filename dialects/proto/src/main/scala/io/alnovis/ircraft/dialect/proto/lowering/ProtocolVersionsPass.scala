package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/**
  * Generates a ProtocolVersions utility class with version constants.
  *
  * Produces:
  * {{{
  * public final class ProtocolVersions {
  *   public static final String V1 = "v1";
  *   public static final String V2 = "v2";
  *   public static final String DEFAULT = "v2";
  *   private ProtocolVersions() {}
  * }
  * }}}
  *
  * Reads version list from SchemaVersions attribute on the first InterfaceOp found.
  */
object ProtocolVersionsPass extends Pass:

  val name: String        = "protocol-versions"
  val description: String = "Generates ProtocolVersions utility class with version constants"

  override def isEnabled(context: PassContext): Boolean =
    !context.getBool("skipProtocolVersions")

  def run(module: Module, context: PassContext): PassResult =
    // Find versions from first InterfaceOp's attributes
    val versions = module
      .collect { case i: InterfaceOp => i }
      .headOption
      .flatMap(_.attributes.getStringList(ProtoAttributes.SchemaVersions))
      .getOrElse(Nil)

    if versions.isEmpty then return PassResult(module)

    // Find API package from first FileOp containing an InterfaceOp
    val apiPackage = module.topLevel
      .collectFirst:
        case f: FileOp if f.types.exists(_.isInstanceOf[InterfaceOp]) => f.packageName
      .getOrElse("com.example.api")

    val versionFields = versions.map: v =>
      FieldDeclOp(
        v.toUpperCase,
        TypeRef.STRING,
        Set(Modifier.Public, Modifier.Static, Modifier.Final),
        defaultValue = Some(s""""$v"""")
      )

    val defaultField = FieldDeclOp(
      "DEFAULT",
      TypeRef.STRING,
      Set(Modifier.Public, Modifier.Static, Modifier.Final),
      defaultValue = Some(s""""${versions.last}"""")
    )

    val privateCtor = ConstructorOp(
      parameters = Nil,
      modifiers = Set(Modifier.Private)
    )

    val cls = ClassOp(
      name = "ProtocolVersions",
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = (versionFields :+ defaultField).toVector,
      constructors = Vector(privateCtor)
    )

    val file = FileOp(apiPackage, Vector(cls))
    PassResult(module.copy(topLevel = module.topLevel :+ file))
