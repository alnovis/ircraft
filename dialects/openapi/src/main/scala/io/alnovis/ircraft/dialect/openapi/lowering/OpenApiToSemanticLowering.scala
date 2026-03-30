package io.alnovis.ircraft.dialect.openapi.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect
import io.alnovis.ircraft.dialect.openapi.ops.*
import io.alnovis.ircraft.core.semantic.SemanticDialect
import io.alnovis.ircraft.core.semantic.ops.*
import io.alnovis.ircraft.core.semantic.expr.Expression

/** Lowers OpenAPI IR to Semantic IR (classes, interfaces, enums). */
object OpenApiToSemanticLowering extends Lowering:

  val name: String             = "openapi-to-semantic"
  val description: String      = "Lowers OpenAPI IR to Semantic IR"
  val sourceDialect: Dialect   = OpenApiDialect
  val targetDialect: Dialect   = SemanticDialect

  def run(module: IrModule, context: PassContext): PassResult =
    val newTopLevel = module.topLevel.flatMap:
      case s: OpenApiSpecOp => lowerSpec(s)
      case other            => Vector(other)
    PassResult(IrModule(module.name, newTopLevel, module.attributes, module.span))

  // -- OpenApiSpecOp -> Vector[FileOp] -----------------------------------------

  private def lowerSpec(spec: OpenApiSpecOp): Vector[FileOp] =
    val specAttr = AttributeMap(
      Attribute.StringAttr("openapi.title", spec.title),
      Attribute.StringAttr("openapi.version", spec.version)
    )

    val schemaFiles = spec.schemas.flatMap:
      case obj: SchemaObjectOp      => Vector(FileOp("", Vector(lowerSchemaObject(obj)), specAttr))
      case enumOp: SchemaEnumOp     => Vector(FileOp("", Vector(lowerSchemaEnum(enumOp)), specAttr))
      case comp: SchemaCompositionOp => lowerComposition(comp, specAttr)
      case _                        => Vector.empty

    val apiFiles = lowerPaths(spec.paths, spec.securitySchemes, specAttr)

    schemaFiles ++ apiFiles

  // -- SchemaObjectOp -> ClassOp -----------------------------------------------

  private def lowerSchemaObject(obj: SchemaObjectOp): ClassOp =
    val fields = obj.properties.map: prop =>
      FieldDeclOp(
        name = OpenApiNameUtils.toCamelCase(prop.name),
        fieldType = prop.fieldType,
        modifiers = Set(Modifier.Private, Modifier.Final),
        defaultValue = prop.defaultValue
      )

    val getters = obj.properties.map: prop =>
      MethodOp(
        name = OpenApiNameUtils.getterName(prop.name),
        returnType = prop.fieldType,
        modifiers = Set(Modifier.Public),
        attributes = AttributeMap(
          Attribute.StringAttr("openapi.propertyName", prop.name),
          Attribute.BoolAttr("openapi.required", prop.required)
        )
      )

    val requiredParams = obj.properties.filter(_.required).map: prop =>
      Parameter(OpenApiNameUtils.toCamelCase(prop.name), prop.fieldType)

    val constructors =
      if requiredParams.nonEmpty then
        Vector(ConstructorOp(parameters = requiredParams.toList, modifiers = Set(Modifier.Public)))
      else Vector.empty

    ClassOp(
      name = OpenApiNameUtils.toPascalCase(obj.name),
      fields = fields,
      constructors = constructors,
      methods = getters,
      attributes = LoweringAttributes.sourceKindAttr(obj)
    )

  // -- SchemaEnumOp -> EnumClassOp ---------------------------------------------

  private def lowerSchemaEnum(enumOp: SchemaEnumOp): EnumClassOp =
    val constants = enumOp.values.map: v =>
      EnumConstantOp(
        name = OpenApiNameUtils.toEnumConstantName(v.name),
        arguments = List(Expression.Literal(v.value, TypeRef.STRING))
      )
    buildEnumClassOp(OpenApiNameUtils.toPascalCase(enumOp.name), constants, LoweringAttributes.sourceKindAttr(enumOp))

  // -- SchemaCompositionOp -> types --------------------------------------------

  private def lowerComposition(comp: SchemaCompositionOp, attrs: AttributeMap): Vector[FileOp] =
    comp.compositionKind match
      case CompositionKind.OneOf =>
        lowerOneOf(comp, attrs)
      case CompositionKind.AllOf =>
        lowerAllOf(comp, attrs)
      case CompositionKind.AnyOf =>
        // AnyOf treated like OneOf for code generation purposes
        lowerOneOf(comp, attrs)

  private def lowerOneOf(comp: SchemaCompositionOp, attrs: AttributeMap): Vector[FileOp] =
    // Use discriminator property name or default
    val interfaceName = comp.discriminatorProperty
      .map(OpenApiNameUtils.toPascalCase)
      .getOrElse("Base")

    val markerInterface = InterfaceOp(
      name = interfaceName,
      attributes = LoweringAttributes.sourceKindAttr(comp)
    )

    val interfaceFile = FileOp("", Vector(markerInterface), attrs)

    // Each schema in the oneOf becomes a class implementing the interface
    val memberFiles = comp.schemas.collect:
      case obj: SchemaObjectOp =>
        val lowered = lowerSchemaObject(obj)
        val rebuilt = ClassOp(
          name = OpenApiNameUtils.toPascalCase(obj.name),
          implementsTypes = List(TypeRef.NamedType(interfaceName)),
          fields = lowered.fields,
          constructors = lowered.constructors,
          methods = lowered.methods,
          attributes = LoweringAttributes.sourceKindAttr(obj)
        )
        FileOp("", Vector(rebuilt), attrs)

    Vector(interfaceFile) ++ memberFiles

  private def lowerAllOf(comp: SchemaCompositionOp, attrs: AttributeMap): Vector[FileOp] =
    // AllOf: first schema is base, subsequent schemas add fields
    val schemaObjects = comp.schemas.collect { case obj: SchemaObjectOp => obj }
    schemaObjects match
      case first +: rest if rest.nonEmpty =>
        val baseClass = lowerSchemaObject(first)
        val baseName = OpenApiNameUtils.toPascalCase(first.name)
        val baseFile = FileOp("", Vector(baseClass), attrs)

        val derivedFiles = rest.map: obj =>
          val derived = ClassOp(
            name = OpenApiNameUtils.toPascalCase(obj.name),
            superClass = Some(TypeRef.NamedType(baseName)),
            fields = obj.properties.map: prop =>
              FieldDeclOp(
                name = OpenApiNameUtils.toCamelCase(prop.name),
                fieldType = prop.fieldType,
                modifiers = Set(Modifier.Private, Modifier.Final)
              ),
            methods = obj.properties.map: prop =>
              MethodOp(
                name = OpenApiNameUtils.getterName(prop.name),
                returnType = prop.fieldType,
                modifiers = Set(Modifier.Public)
              ),
            attributes = LoweringAttributes.sourceKindAttr(obj)
          )
          FileOp("", Vector(derived), attrs)

        Vector(baseFile) ++ derivedFiles
      case _ =>
        schemaObjects.map: obj =>
          FileOp("", Vector(lowerSchemaObject(obj)), attrs)

  // -- PathOp + OperationOp -> InterfaceOp -------------------------------------

  private def lowerPaths(
    paths: Vector[PathOp],
    securitySchemes: Vector[SecuritySchemeOp],
    attrs: AttributeMap
  ): Vector[FileOp] =
    // Group operations by their first tag to create API interfaces
    val allOperations = paths.flatMap: pathOp =>
      pathOp.operations.map(op => (pathOp.pathPattern, op))

    val grouped = allOperations.groupBy: (_, op) =>
      OpenApiNameUtils.apiInterfaceName(op.tags)

    grouped.map { (interfaceName, ops) =>
      val methods = ops.map((pathPattern, op) => lowerOperation(pathPattern, op))

      val securityAttrs =
        if securitySchemes.nonEmpty then
          val schemeNames = securitySchemes.map(_.name).mkString(", ")
          AttributeMap(Attribute.StringAttr("openapi.securitySchemes", schemeNames))
        else AttributeMap.empty

      val combinedAttrs = attrs ++ securityAttrs

      FileOp("", Vector(InterfaceOp(
        name = interfaceName,
        methods = methods,
        attributes = combinedAttrs
      )), attrs)
    }.toVector

  private def lowerOperation(pathPattern: String, op: OperationOp): MethodOp =
    val methodName = op.operationId
      .map(OpenApiNameUtils.toCamelCase)
      .getOrElse(op.httpMethod.toString.toLowerCase + "Operation")

    // Parameters from path/query/header params
    val parameterList = op.parameters.map: param =>
      Parameter(OpenApiNameUtils.toCamelCase(param.name), param.paramType)

    // Add request body as a parameter
    val bodyParams = op.requestBodies.flatMap: rb =>
      rb.mediaTypes.headOption.map: mt =>
        Parameter("body", mt.schemaRef)

    val allParams = (parameterList ++ bodyParams).toList

    // Return type from primary response (200 or first)
    val returnType = op.responses
      .find(r => r.statusCode == "200" || r.statusCode == "201")
      .orElse(op.responses.headOption)
      .flatMap(_.mediaTypes.headOption)
      .map(_.schemaRef)
      .getOrElse(TypeRef.VOID)

    val opAttrs = AttributeMap(
      Attribute.StringAttr("openapi.httpMethod", op.httpMethod.toString),
      Attribute.StringAttr("openapi.path", pathPattern)
    )

    val modifiers: Set[Modifier] =
      if op.deprecated then Set(Modifier.Public, Modifier.Abstract)
      else Set(Modifier.Public, Modifier.Abstract)

    val annotations =
      if op.deprecated then List("Deprecated")
      else Nil

    MethodOp(
      name = methodName,
      returnType = returnType,
      parameters = allParams,
      modifiers = modifiers,
      javadoc = op.summary.orElse(op.description),
      annotations = annotations,
      attributes = opAttrs
    )

  // -- Shared helpers -----------------------------------------------------------

  private def buildEnumClassOp(
    name: String,
    constants: Vector[EnumConstantOp],
    attributes: AttributeMap = AttributeMap.empty
  ): EnumClassOp =
    EnumClassOp(
      name = name,
      constants = constants,
      fields = Vector(
        FieldDeclOp("value", TypeRef.STRING, modifiers = Set(Modifier.Private, Modifier.Final))
      ),
      constructors = Vector(
        ConstructorOp(
          parameters = List(Parameter("value", TypeRef.STRING)),
          modifiers = Set(Modifier.Private)
        )
      ),
      methods = Vector(
        MethodOp("getValue", TypeRef.STRING, modifiers = Set(Modifier.Public))
      ),
      attributes = attributes
    )
