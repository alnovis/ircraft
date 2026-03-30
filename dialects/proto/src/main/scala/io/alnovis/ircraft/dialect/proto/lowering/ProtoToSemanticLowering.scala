package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.core.semantic.SemanticDialect
import io.alnovis.ircraft.core.semantic.ops.*
import io.alnovis.ircraft.core.semantic.expr.Expression

/** Lowers Proto IR to Semantic IR (interfaces + enum classes). */
object ProtoToSemanticLowering extends Lowering:

  val name: String             = "proto-to-semantic"
  val description: String      = "Lowers Proto IR to Semantic IR"
  val sourceDialect: Dialect   = ProtoDialect
  val targetDialect: Dialect   = SemanticDialect

  def run(module: IrModule, context: PassContext): PassResult =
    val newTopLevel = module.topLevel.flatMap:
      case f: ProtoFileOp => lowerProtoFile(f)
      case other          => Vector(other)
    PassResult(IrModule(module.name, newTopLevel, module.attributes, module.span))

  // -- ProtoFileOp -> Vector[FileOp] ------------------------------------------

  private def lowerProtoFile(file: ProtoFileOp): Vector[FileOp] =
    val syntaxAttr = AttributeMap(Attribute.StringAttr("proto.syntax", file.syntax.toString))

    val messageFiles = file.messages.map: msg =>
      FileOp(file.protoPackage, Vector(lowerMessage(msg, file.syntax)), syntaxAttr)

    val enumFiles = file.enums.map: e =>
      FileOp(file.protoPackage, Vector(lowerEnumOp(e)), syntaxAttr)

    messageFiles ++ enumFiles

  // -- MessageOp -> InterfaceOp -----------------------------------------------

  private def lowerMessage(msg: MessageOp, syntax: ProtoSyntax): InterfaceOp =
    val fieldMethods = msg.fields.flatMap(f => lowerField(f, syntax))

    val oneofResults = msg.oneofs.map(o => lowerOneof(o))
    val oneofMethods = oneofResults.flatMap(_._1)
    val oneofEnums = oneofResults.map(_._2)

    val nestedInterfaces = msg.nestedMessages.map(m => lowerMessage(m, syntax))
    val nestedEnums = msg.nestedEnums.map(lowerEnumOp)

    val allNestedTypes: Vector[Operation] = nestedInterfaces ++ nestedEnums ++ oneofEnums

    InterfaceOp(
      name = msg.name,
      methods = fieldMethods ++ oneofMethods,
      nestedTypes = allNestedTypes,
      attributes = LoweringAttributes.sourceKindAttr(msg)
    )

  // -- FieldOp -> Vector[MethodOp] --------------------------------------------

  private def lowerField(field: FieldOp, syntax: ProtoSyntax): Vector[MethodOp] =
    val (returnType, _) = unwrapType(field.fieldType)

    val fieldAttrs = AttributeMap(
      Attribute.IntAttr("proto.fieldNumber", field.number),
      Attribute.StringAttr("proto.fieldName", field.name)
    )

    val getter = MethodOp(
      name = ProtoNameUtils.getterName(field.name),
      returnType = returnType,
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      attributes = fieldAttrs
    )

    if shouldGenerateHasMethod(field.fieldType, syntax) then
      val hasMethod = MethodOp(
        name = ProtoNameUtils.hasMethodName(field.name),
        returnType = TypeRef.BOOL,
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        attributes = fieldAttrs
      )
      Vector(getter, hasMethod)
    else
      Vector(getter)

  private def unwrapType(fieldType: TypeRef): (TypeRef, Boolean) =
    fieldType match
      case TypeRef.OptionalType(inner) => (inner, true)
      case other                       => (other, false)

  private def shouldGenerateHasMethod(fieldType: TypeRef, syntax: ProtoSyntax): Boolean =
    fieldType match
      case _: TypeRef.ListType       => false
      case _: TypeRef.MapType        => false
      case TypeRef.OptionalType(_)   => true
      case _: TypeRef.NamedType      => syntax == ProtoSyntax.Proto3
      case _                         => false

  // -- OneofOp -> (methods, case enum) ----------------------------------------

  private def lowerOneof(oneof: OneofOp): (Vector[MethodOp], EnumClassOp) =
    val enumName = ProtoNameUtils.caseEnumName(oneof.name)

    val fieldConstants = oneof.fields.map: f =>
      EnumConstantOp(
        name = f.name.toUpperCase,
        arguments = List(Expression.Literal(f.number.toString, TypeRef.INT))
      )

    val notSetConstant = EnumConstantOp(
      name = oneof.name.toUpperCase + "_NOT_SET",
      arguments = List(Expression.Literal("0", TypeRef.INT))
    )

    val caseEnum = buildEnumClassOp(enumName, fieldConstants :+ notSetConstant)

    val discriminatorGetter = MethodOp(
      name = ProtoNameUtils.caseEnumGetterName(oneof.name),
      returnType = TypeRef.NamedType(enumName),
      modifiers = Set(Modifier.Public, Modifier.Abstract)
    )

    val perCaseGetters = oneof.fields.map: f =>
      MethodOp(
        name = ProtoNameUtils.getterName(f.name),
        returnType = f.fieldType,
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        attributes = AttributeMap(
          Attribute.IntAttr("proto.fieldNumber", f.number),
          Attribute.StringAttr("proto.fieldName", f.name)
        )
      )

    (Vector(discriminatorGetter) ++ perCaseGetters, caseEnum)

  // -- EnumOp -> EnumClassOp --------------------------------------------------

  private def lowerEnumOp(protoEnum: EnumOp): EnumClassOp =
    val constants = protoEnum.values.map: v =>
      EnumConstantOp(
        name = v.name,
        arguments = List(Expression.Literal(v.number.toString, TypeRef.INT))
      )
    buildEnumClassOp(protoEnum.name, constants, LoweringAttributes.sourceKindAttr(protoEnum))

  // -- Shared helpers ---------------------------------------------------------

  private def buildEnumClassOp(
    name: String,
    constants: Vector[EnumConstantOp],
    attributes: AttributeMap = AttributeMap.empty
  ): EnumClassOp =
    EnumClassOp(
      name = name,
      constants = constants,
      fields = Vector(
        FieldDeclOp("value", TypeRef.INT, modifiers = Set(Modifier.Private, Modifier.Final))
      ),
      constructors = Vector(
        ConstructorOp(
          parameters = List(Parameter("value", TypeRef.INT)),
          modifiers = Set(Modifier.Private)
        )
      ),
      methods = Vector(
        MethodOp("getValue", TypeRef.INT, modifiers = Set(Modifier.Public))
      ),
      attributes = attributes
    )
