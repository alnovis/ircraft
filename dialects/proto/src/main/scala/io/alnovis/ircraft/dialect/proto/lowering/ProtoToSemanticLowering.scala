package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/**
  * Lowers Proto Dialect IR to Semantic Dialect IR.
  *
  * Transforms:
  *   - SchemaOp → List[FileOp]
  *   - MessageOp → InterfaceOp + ClassOp(abstract) + ClassOp(impl per version)
  *   - FieldOp → MethodOp(getter) in interface + FieldDeclOp + MethodOp(extract) in abstract
  *   - EnumOp → EnumClassOp
  */
class ProtoToSemanticLowering(config: LoweringConfig) extends Lowering:

  val name: String           = "proto-to-semantic"
  val description: String    = "Lowers proto schema to language-agnostic OOP types"
  val sourceDialect: Dialect = ProtoDialect
  val targetDialect: Dialect = SemanticDialect

  def run(module: Module, context: PassContext): PassResult =
    val allOps   = Vector.newBuilder[Operation]
    val allDiags = List.newBuilder[DiagnosticMessage]

    for op <- module.topLevel do
      op match
        case s: SchemaOp =>
          val (ops, diags) = lowerSchema(s)
          allOps ++= ops
          allDiags ++= diags
        case other => allOps += other

    PassResult(module.copy(topLevel = allOps.result()), allDiags.result())

  private def lowerSchema(schema: SchemaOp): (Vector[Operation], List[DiagnosticMessage]) =
    val ops   = Vector.newBuilder[Operation]
    val diags = List.newBuilder[DiagnosticMessage]

    for e <- schema.enums do
      scala.util.Try(lowerEnum(e)) match
        case scala.util.Success(file) => ops += file
        case scala.util.Failure(ex)   => diags += DiagnosticMessage.error(s"Failed to lower enum '${e.name}': ${ex.getMessage}")

    for c <- schema.conflictEnums do
      scala.util.Try(lowerConflictEnum(c)) match
        case scala.util.Success(file) => ops += file
        case scala.util.Failure(ex)   => diags += DiagnosticMessage.error(s"Failed to lower conflict enum '${c.enumName}': ${ex.getMessage}")

    for m <- schema.messages do
      scala.util.Try(lowerMessage(m, schema.versions)) match
        case scala.util.Success(files) => ops ++= files
        case scala.util.Failure(ex)    => diags += DiagnosticMessage.error(s"Failed to lower message '${m.name}': ${ex.getMessage}")

    (ops.result(), diags.result())

  // ── Enum lowering ──────────────────────────────────────────────────────

  private def lowerEnum(e: EnumOp): FileOp =
    val constants = e.values.map: v =>
      EnumConstantOp(v.name, List(Expression.Literal(v.number.toString, TypeRef.INT)))
    val valueField = FieldDeclOp("value", TypeRef.INT, Set(Modifier.Private, Modifier.Final))
    val constructor = ConstructorOp(
      parameters = List(Parameter("value", TypeRef.INT)),
      modifiers = Set(Modifier.Private)
    )
    val getter = MethodOp("getValue", TypeRef.INT, modifiers = Set(Modifier.Public))

    val enumClass = EnumClassOp(
      name = e.name,
      constants = constants,
      fields = Vector(valueField),
      constructors = Vector(constructor),
      methods = Vector(getter)
    )
    FileOp(config.apiPackage, Vector(enumClass))

  private def lowerConflictEnum(c: ConflictEnumOp): FileOp =
    val constants = c.values.map: v =>
      EnumConstantOp(v.name, List(Expression.Literal(v.number.toString, TypeRef.INT)))
    val enumClass = EnumClassOp(
      name = c.enumName,
      constants = constants,
      fields = Vector(FieldDeclOp("value", TypeRef.INT, Set(Modifier.Private, Modifier.Final))),
      constructors = Vector(
        ConstructorOp(
          parameters = List(Parameter("value", TypeRef.INT)),
          modifiers = Set(Modifier.Private)
        )
      ),
      methods = Vector(MethodOp("getValue", TypeRef.INT, modifiers = Set(Modifier.Public)))
    )
    FileOp(config.apiPackage, Vector(enumClass))

  // ── Message lowering ───────────────────────────────────────────────────

  private def lowerMessage(msg: MessageOp, versions: List[String]): Vector[FileOp] =
    val interfaceFile = lowerToInterface(msg)
    val abstractFile  = lowerToAbstractClass(msg)
    val implFiles = versions
      .filter(v => msg.presentInVersions.contains(v))
      .map(v => lowerToImplClass(msg, v))
    Vector(interfaceFile, abstractFile) ++ implFiles

  private def lowerToInterface(msg: MessageOp): FileOp =
    val getters = msg.fields.map(fieldToGetter)

    val nestedEnums = msg.nestedEnums.map: e =>
      val constants =
        e.values.map(v => EnumConstantOp(v.name, List(Expression.Literal(v.number.toString, TypeRef.INT))))
      EnumClassOp(e.name, constants = constants)

    val oneofEnums = msg.oneofs.map: o =>
      val constants = o.fields.map(f => EnumConstantOp(f.javaName.capitalize, Nil))
      val notSet    = EnumConstantOp(s"${o.javaName.capitalize}_NOT_SET".toUpperCase, Nil)
      EnumClassOp(o.caseEnumName, constants = constants :+ notSet)

    val oneofDiscriminators = msg.oneofs.map: o =>
      MethodOp(
        s"get${o.caseEnumName}",
        TypeRef.NamedType(o.caseEnumName),
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )

    val nestedInterfaces = msg.nestedMessages.map: nested =>
      lowerToInterfaceOp(nested)

    val iface = InterfaceOp(
      name = msg.name,
      methods = getters ++ oneofDiscriminators,
      nestedTypes =
        (nestedEnums: Vector[Operation]) ++ (oneofEnums: Vector[Operation]) ++ (nestedInterfaces: Vector[Operation])
    )
    FileOp(config.apiPackage, Vector(iface))

  private def lowerToInterfaceOp(msg: MessageOp): InterfaceOp =
    val getters = msg.fields.map(fieldToGetter)
    InterfaceOp(name = msg.name, methods = getters)

  private def lowerToAbstractClass(msg: MessageOp): FileOp =
    val protoTypeParam = TypeParam("PROTO", upperBounds = List(TypeRef.NamedType("com.google.protobuf.Message")))
    val protoField     = FieldDeclOp("proto", TypeRef.NamedType("PROTO"), Set(Modifier.Protected, Modifier.Final))

    val constructor = ConstructorOp(
      parameters = List(Parameter("proto", TypeRef.NamedType("PROTO"))),
      modifiers = Set(Modifier.Protected),
      body = Some(
        Block.of(
          Statement.Assignment(
            Expression.FieldAccess(Expression.ThisRef, "proto"),
            Expression.Identifier("proto")
          )
        )
      )
    )

    val extractMethods = msg.fields.map: f =>
      MethodOp(
        s"extract${f.javaName.capitalize}",
        resolveGetterType(f),
        modifiers = Set(Modifier.Protected, Modifier.Abstract)
      )

    val getterImpls = msg.fields.map: f =>
      MethodOp(
        s"get${f.javaName.capitalize}",
        resolveGetterType(f),
        modifiers = Set(Modifier.Public, Modifier.Override),
        body = Some(
          Block.of(
            Statement.ReturnStmt(Some(Expression.MethodCall(None, s"extract${f.javaName.capitalize}")))
          )
        )
      )

    val cls = ClassOp(
      name = s"${config.abstractClassPrefix}${msg.name}",
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      typeParams = List(protoTypeParam),
      implementsTypes = List(TypeRef.NamedType(msg.name)),
      fields = Vector(protoField),
      constructors = Vector(constructor),
      methods = extractMethods ++ getterImpls
    )
    FileOp(config.implSubPackage, Vector(cls))

  private def lowerToImplClass(msg: MessageOp, version: String): FileOp =
    val versionSuffix = version.capitalize
    val protoTypeName = s"${msg.name}Proto" // simplified; real mapping is more complex

    val constructor = ConstructorOp(
      parameters = List(Parameter("proto", TypeRef.NamedType(protoTypeName))),
      modifiers = Set(Modifier.Public),
      body = Some(
        Block.of(
          Statement.ExpressionStmt(
            Expression.MethodCall(Some(Expression.SuperRef), "this", List(Expression.Identifier("proto")))
          )
        )
      )
    )

    val extractImpls = msg.fields
      .filter(_.presentInVersions.contains(version))
      .map: f =>
        MethodOp(
          s"extract${f.javaName.capitalize}",
          resolveGetterType(f),
          modifiers = Set(Modifier.Protected, Modifier.Override),
          body = Some(
            Block.of(
              Statement.ReturnStmt(
                Some(
                  Expression.MethodCall(Some(Expression.Identifier("proto")), s"get${f.javaName.capitalize}")
                )
              )
            )
          )
        )

    val cls = ClassOp(
      name = s"${msg.name}$versionSuffix",
      modifiers = Set(Modifier.Public),
      superClass = Some(
        TypeRef.ParameterizedType(
          TypeRef.NamedType(s"${config.abstractClassPrefix}${msg.name}"),
          List(TypeRef.NamedType(protoTypeName))
        )
      ),
      constructors = Vector(constructor),
      methods = extractImpls
    )
    FileOp(config.implPackage(version), Vector(cls))

  // ── Field helpers ──────────────────────────────────────────────────────

  private def fieldToGetter(f: FieldOp): MethodOp =
    val returnType = resolveGetterType(f)
    MethodOp(
      s"get${f.javaName.capitalize}",
      returnType,
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      javadoc = Some(s"Returns the ${f.name} value.")
    )

  private def resolveGetterType(f: FieldOp): TypeRef =
    if f.isRepeated then TypeRef.ListType(f.fieldType)
    else if f.isMap then f.fieldType // MapType already encoded in fieldType
    else f.fieldType
