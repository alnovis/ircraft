package io.alnovis.ircraft.dialect.graphql.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.GraphQlDialect
import io.alnovis.ircraft.dialect.graphql.ops.*
import io.alnovis.ircraft.core.semantic.SemanticDialect
import io.alnovis.ircraft.core.semantic.ops.*

/** Lowers GraphQL IR to Semantic IR (interfaces, classes, enums). */
object GraphQlToSemanticLowering extends Lowering:

  val name: String             = "graphql-to-semantic"
  val description: String      = "Lowers GraphQL IR to Semantic IR"
  val sourceDialect: Dialect   = GraphQlDialect
  val targetDialect: Dialect   = SemanticDialect

  def run(module: IrModule, context: PassContext): PassResult =
    val newTopLevel = module.topLevel.flatMap:
      case s: GqlSchemaOp => lowerSchema(s)
      case other          => Vector(other)
    PassResult(IrModule(module.name, newTopLevel, module.attributes, module.span))

  // -- GqlSchemaOp -> Vector[FileOp] -------------------------------------------

  private def lowerSchema(schema: GqlSchemaOp): Vector[FileOp] =
    val schemaAttr = AttributeMap(
      Attribute.StringAttr("graphql.queryType", schema.queryType)
    )

    schema.types.flatMap:
      case obj: ObjectTypeOp      => Vector(FileOp("", Vector(lowerObjectType(obj)), schemaAttr))
      case inp: InputObjectTypeOp => Vector(FileOp("", Vector(lowerInputObjectType(inp)), schemaAttr))
      case ifc: InterfaceTypeOp   => Vector(FileOp("", Vector(lowerInterfaceType(ifc)), schemaAttr))
      case uni: UnionTypeOp       => Vector(FileOp("", Vector(lowerUnionType(uni)), schemaAttr))
      case enm: EnumTypeOp        => Vector(FileOp("", Vector(lowerEnumType(enm)), schemaAttr))
      case _: ScalarTypeOp        => Vector.empty
      case _                      => Vector.empty

  // -- ObjectTypeOp -> InterfaceOp (getters for fields) -----------------------

  private def lowerObjectType(obj: ObjectTypeOp): InterfaceOp =
    val methods = obj.fields.map(lowerFieldToMethod)
    val extendsTypes = obj.implementsInterfaces.map(TypeRef.NamedType(_))

    InterfaceOp(
      name = obj.name,
      extendsTypes = extendsTypes,
      methods = methods,
      attributes = LoweringAttributes.sourceKindAttr(obj)
    )

  // -- InputObjectTypeOp -> ClassOp (fields + constructor) --------------------

  private def lowerInputObjectType(inp: InputObjectTypeOp): ClassOp =
    val fields = inp.fields.map: f =>
      FieldDeclOp(
        name = f.name,
        fieldType = f.fieldType,
        modifiers = Set(Modifier.Private, Modifier.Final),
        defaultValue = f.defaultValue
      )

    val getters = inp.fields.map: f =>
      MethodOp(
        name = f.name,
        returnType = f.fieldType,
        modifiers = Set(Modifier.Public)
      )

    val constructorParams = inp.fields.map: f =>
      Parameter(f.name, f.fieldType)

    val constructors =
      if constructorParams.nonEmpty then
        Vector(ConstructorOp(parameters = constructorParams.toList, modifiers = Set(Modifier.Public)))
      else Vector.empty

    ClassOp(
      name = inp.name,
      fields = fields,
      constructors = constructors,
      methods = getters,
      attributes = LoweringAttributes.sourceKindAttr(inp)
    )

  // -- InterfaceTypeOp -> InterfaceOp (abstract, same as ObjectType) ----------

  private def lowerInterfaceType(ifc: InterfaceTypeOp): InterfaceOp =
    val methods = ifc.fields.map(lowerFieldToMethod)
    val extendsTypes = ifc.implementsInterfaces.map(TypeRef.NamedType(_))

    InterfaceOp(
      name = ifc.name,
      extendsTypes = extendsTypes,
      methods = methods,
      attributes = LoweringAttributes.sourceKindAttr(ifc)
    )

  // -- UnionTypeOp -> InterfaceOp (sealed marker) -----------------------------

  private def lowerUnionType(uni: UnionTypeOp): InterfaceOp =
    InterfaceOp(
      name = uni.name,
      annotations = List("Sealed"),
      attributes = LoweringAttributes.sourceKindAttr(uni)
    )

  // -- EnumTypeOp -> EnumClassOp ----------------------------------------------

  private def lowerEnumType(enm: EnumTypeOp): EnumClassOp =
    val constants = enm.values.map: v =>
      val attrs =
        if v.isDeprecated then
          val reason = v.deprecationReason.getOrElse("No longer supported")
          AttributeMap(
            Attribute.BoolAttr("graphql.isDeprecated", true),
            Attribute.StringAttr("graphql.deprecationReason", reason)
          )
        else AttributeMap.empty
      EnumConstantOp(name = v.name, attributes = attrs)

    EnumClassOp(
      name = enm.name,
      constants = constants,
      attributes = LoweringAttributes.sourceKindAttr(enm)
    )

  // -- GqlFieldOp -> MethodOp -------------------------------------------------

  private def lowerFieldToMethod(field: GqlFieldOp): MethodOp =
    val params = field.arguments.map: arg =>
      Parameter(arg.name, arg.argType)

    val deprecationAttrs =
      if field.isDeprecated then
        val reason = field.deprecationReason.getOrElse("No longer supported")
        AttributeMap(
          Attribute.BoolAttr("graphql.isDeprecated", true),
          Attribute.StringAttr("graphql.deprecationReason", reason)
        )
      else AttributeMap.empty

    val annotations =
      if field.isDeprecated then List("Deprecated")
      else Nil

    MethodOp(
      name = field.name,
      returnType = field.fieldType,
      parameters = params.toList,
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      javadoc = field.description,
      annotations = annotations,
      attributes = deprecationAttrs
    )
