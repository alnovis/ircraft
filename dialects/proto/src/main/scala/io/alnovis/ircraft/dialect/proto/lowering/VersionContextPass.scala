package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/**
  * Generates VersionContext interface and per-version implementations.
  *
  * VersionContext is a factory for wrapping proto messages and parsing bytes. Produces:
  *
  *   - `VersionContext` interface with `wrapXxx(Message)`, `parseXxxFromBytes(byte[])` per message
  *   - `VersionContextV1`, `VersionContextV2`, etc. with concrete implementations
  */
object VersionContextPass extends Pass:

  val name: String        = "version-context"
  val description: String = "Generates VersionContext interface and per-version implementations"

  override def isEnabled(context: PassContext): Boolean =
    !context.getBool("skipVersionContext")

  def run(module: Module, context: PassContext): PassResult =
    // Collect message names and versions from InterfaceOps
    val messageInterfaces = module
      .collect { case i: InterfaceOp => i }
      .filter(_.attributes.contains(ProtoAttributes.PresentInVersions))

    if messageInterfaces.isEmpty then return PassResult(module)

    val versions = messageInterfaces.head.attributes
      .getStringList(ProtoAttributes.SchemaVersions)
      .getOrElse(Nil)

    if versions.isEmpty then return PassResult(module)

    // Find API and impl packages
    val apiPackage = module.topLevel
      .collectFirst:
        case f: FileOp if f.types.exists(_.isInstanceOf[InterfaceOp]) => f.packageName
      .getOrElse("com.example.api")

    // Generate VersionContext interface
    val interfaceMethods = messageInterfaces.flatMap: iface =>
      Vector(
        MethodOp(
          s"wrap${iface.name}",
          TypeRef.NamedType(iface.name),
          parameters = List(Parameter("proto", TypeRef.NamedType("com.google.protobuf.Message"))),
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some(s"Wraps a proto message as ${iface.name}.")
        ),
        MethodOp(
          s"parse${iface.name}FromBytes",
          TypeRef.NamedType(iface.name),
          parameters = List(Parameter("bytes", TypeRef.BYTES)),
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some(s"Parses bytes into ${iface.name}.")
        )
      )

    val versionIdMethod = MethodOp(
      "getVersionId",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      javadoc = Some("Returns the version identifier for this context.")
    )

    val contextInterface = InterfaceOp(
      name = "VersionContext",
      methods = (versionIdMethod +: interfaceMethods).toVector,
      javadoc = Some("Factory for wrapping proto messages and parsing bytes for a specific version.")
    )

    val contextFile = FileOp(apiPackage, Vector(contextInterface))

    // Generate per-version implementations
    val implFiles = versions.map: version =>
      generateVersionImpl(version, messageInterfaces, apiPackage)

    PassResult(module.copy(topLevel = module.topLevel ++ (contextFile +: implFiles.toVector)))

  private def generateVersionImpl(
    version: String,
    messages: Vector[InterfaceOp],
    apiPackage: String
  ): FileOp =
    val versionSuffix = version.capitalize
    val implClassName = s"VersionContext$versionSuffix"

    val instanceField = FieldDeclOp(
      "INSTANCE",
      TypeRef.NamedType(implClassName),
      Set(Modifier.Public, Modifier.Static, Modifier.Final),
      defaultValue = Some(s"new $implClassName()")
    )

    val privateCtor = ConstructorOp(
      parameters = Nil,
      modifiers = Set(Modifier.Private)
    )

    val versionIdMethod = MethodOp(
      "getVersionId",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public, Modifier.Override),
      body = Some(
        Block.of(
          Statement.ReturnStmt(Some(Expression.Literal(s""""$version"""", TypeRef.STRING)))
        )
      )
    )

    val wrapMethods = messages.flatMap: iface =>
      val msgVersions = iface.attributes.getStringList(ProtoAttributes.PresentInVersions).getOrElse(Nil)
      if msgVersions.contains(version) then
        Vector(
          MethodOp(
            s"wrap${iface.name}",
            TypeRef.NamedType(iface.name),
            parameters = List(Parameter("proto", TypeRef.NamedType("com.google.protobuf.Message"))),
            modifiers = Set(Modifier.Public, Modifier.Override),
            body = Some(
              Block.of(
                Statement.ReturnStmt(
                  Some(
                    Expression.NewInstance(
                      TypeRef.NamedType(s"${iface.name}$versionSuffix"),
                      List(
                        Expression.Cast(
                          Expression.Identifier("proto"),
                          TypeRef.NamedType(s"${iface.name}Proto")
                        )
                      )
                    )
                  )
                )
              )
            )
          ),
          MethodOp(
            s"parse${iface.name}FromBytes",
            TypeRef.NamedType(iface.name),
            parameters = List(Parameter("bytes", TypeRef.BYTES)),
            modifiers = Set(Modifier.Public, Modifier.Override),
            body = Some(
              Block.of(
                Statement.ReturnStmt(
                  Some(
                    Expression.NewInstance(
                      TypeRef.NamedType(s"${iface.name}$versionSuffix"),
                      List(
                        Expression.MethodCall(
                          Some(Expression.Identifier(s"${iface.name}Proto")),
                          "parseFrom",
                          List(Expression.Identifier("bytes"))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      else Vector.empty

    val cls = ClassOp(
      name = implClassName,
      modifiers = Set(Modifier.Public, Modifier.Final),
      implementsTypes = List(TypeRef.NamedType("VersionContext")),
      fields = Vector(instanceField),
      constructors = Vector(privateCtor),
      methods = (versionIdMethod +: wrapMethods).toVector
    )

    FileOp(apiPackage, Vector(cls))
