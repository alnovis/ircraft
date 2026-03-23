package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Generates SchemaInfo classes per version with runtime schema metadata.
  *
  * Produces per version:
  * {{{
  * public final class SchemaInfoV1 {
  *   public static final SchemaInfoV1 INSTANCE = new SchemaInfoV1();
  *   public String getVersionId() { return "v1"; }
  *   public List<String> getMessageNames() { return List.of("Money", "Order"); }
  *   public List<String> getEnumNames() { return List.of("Currency"); }
  * }
  * }}}
  *
  * Conditional: controlled by PassContext "generateSchemaMetadata".
  */
object SchemaMetadataPass extends Pass:

  val name: String        = "schema-metadata"
  val description: String = "Generates SchemaInfo classes with runtime schema metadata per version"

  override def isEnabled(context: PassContext): Boolean =
    context.getBool("generateSchemaMetadata")

  def run(module: Module, context: PassContext): PassResult =
    val messageInterfaces = module.collect { case i: InterfaceOp => i }
      .filter(_.attributes.contains(ProtoAttributes.PresentInVersions))

    if messageInterfaces.isEmpty then return PassResult(module)

    val versions = messageInterfaces.head.attributes
      .getStringList(ProtoAttributes.SchemaVersions)
      .getOrElse(Nil)

    val apiPackage = module.topLevel.collectFirst:
      case f: FileOp if f.types.exists(_.isInstanceOf[InterfaceOp]) => f.packageName
    .getOrElse("com.example.api")

    val metadataPackage = apiPackage.replace(".api", "") + ".metadata"

    val messageNames = messageInterfaces.map(_.name).toList
    val enumNames = module.collect { case e: EnumClassOp => e }.map(_.name).toList

    val schemaInfoFiles = versions.map: version =>
      generateSchemaInfo(version, messageNames, enumNames, metadataPackage)

    PassResult(module.copy(topLevel = module.topLevel ++ schemaInfoFiles))

  private def generateSchemaInfo(
      version: String,
      messageNames: List[String],
      enumNames: List[String],
      packageName: String
  ): FileOp =
    val versionSuffix = version.capitalize
    val className     = s"SchemaInfo$versionSuffix"

    val instanceField = FieldDeclOp(
      "INSTANCE",
      TypeRef.NamedType(className),
      Set(Modifier.Public, Modifier.Static, Modifier.Final),
      defaultValue = Some(s"new $className()")
    )

    val privateCtor = ConstructorOp(
      parameters = Nil,
      modifiers = Set(Modifier.Private)
    )

    val versionIdMethod = MethodOp(
      "getVersionId",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public),
      body = Some(Block.of(
        Statement.ReturnStmt(Some(Expression.Literal(s""""$version"""", TypeRef.STRING)))
      ))
    )

    val getMessageNames = MethodOp(
      "getMessageNames",
      TypeRef.ListType(TypeRef.STRING),
      modifiers = Set(Modifier.Public),
      body = Some(Block.of(
        Statement.ReturnStmt(Some(Expression.MethodCall(
          Some(Expression.Identifier("java.util.List")),
          "of",
          messageNames.map(n => Expression.Literal(s""""$n"""", TypeRef.STRING))
        )))
      ))
    )

    val getEnumNames = MethodOp(
      "getEnumNames",
      TypeRef.ListType(TypeRef.STRING),
      modifiers = Set(Modifier.Public),
      body = Some(Block.of(
        Statement.ReturnStmt(Some(Expression.MethodCall(
          Some(Expression.Identifier("java.util.List")),
          "of",
          enumNames.map(n => Expression.Literal(s""""$n"""", TypeRef.STRING))
        )))
      ))
    )

    val cls = ClassOp(
      name = className,
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = Vector(instanceField),
      constructors = Vector(privateCtor),
      methods = Vector(versionIdMethod, getMessageNames, getEnumNames)
    )

    FileOp(packageName, Vector(cls))
