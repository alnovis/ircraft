package io.alnovis.ircraft.dialect.graphql.passes

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.ops.*

/** Validates GraphQL dialect IR structural correctness. */
object GraphQlVerifierPass extends Pass:

  val name: String        = "graphql-verifier"
  val description: String = "Validates GraphQL IR structure"

  def run(module: IrModule, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verifyOp).toList
    PassResult(module, diagnostics)

  private def verifyOp(op: Operation): List[DiagnosticMessage] = op match
    case s: GqlSchemaOp       => verifySchema(s)
    case o: ObjectTypeOp      => verifyObjectType(o)
    case i: InputObjectTypeOp => verifyInputObjectType(i)
    case t: InterfaceTypeOp   => verifyInterfaceType(t)
    case u: UnionTypeOp       => verifyUnionType(u)
    case e: EnumTypeOp        => verifyEnumType(e)
    case f: GqlFieldOp        => verifyField(f)
    case _: ScalarTypeOp | _: DirectiveDefOp | _: InputFieldOp |
         _: GqlArgumentOp | _: GqlEnumValueOp =>
      Nil
    case other =>
      List(DiagnosticMessage.warning(s"Unknown operation type: ${other.qualifiedName}", other.span))

  private def verifySchema(s: GqlSchemaOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if s.queryType.isEmpty then
      diags += DiagnosticMessage.error("Schema queryType must not be empty", s.span)
    diags ++= s.types.flatMap(verifyOp)
    diags.result()

  private def verifyObjectType(o: ObjectTypeOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if o.name.isEmpty then
      diags += DiagnosticMessage.error("ObjectType name must not be empty", o.span)
    if o.fields.isEmpty then
      diags += DiagnosticMessage.error(s"ObjectType '${o.name}' must have at least one field", o.span)
    diags ++= o.fields.flatMap(verifyField)
    diags.result()

  private def verifyInputObjectType(i: InputObjectTypeOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if i.name.isEmpty then
      diags += DiagnosticMessage.error("InputObjectType name must not be empty", i.span)
    if i.fields.isEmpty then
      diags += DiagnosticMessage.error(s"InputObjectType '${i.name}' must have at least one field", i.span)
    diags.result()

  private def verifyInterfaceType(t: InterfaceTypeOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if t.name.isEmpty then
      diags += DiagnosticMessage.error("InterfaceType name must not be empty", t.span)
    if t.fields.isEmpty then
      diags += DiagnosticMessage.error(s"InterfaceType '${t.name}' must have at least one field", t.span)
    diags ++= t.fields.flatMap(verifyField)
    diags.result()

  private def verifyUnionType(u: UnionTypeOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if u.name.isEmpty then
      diags += DiagnosticMessage.error("UnionType name must not be empty", u.span)
    if u.memberTypes.isEmpty then
      diags += DiagnosticMessage.error(s"UnionType '${u.name}' must have at least one member", u.span)
    diags.result()

  private def verifyEnumType(e: EnumTypeOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if e.name.isEmpty then
      diags += DiagnosticMessage.error("EnumType name must not be empty", e.span)
    if e.values.isEmpty then
      diags += DiagnosticMessage.error(s"EnumType '${e.name}' must have at least one value", e.span)
    diags.result()

  private def verifyField(f: GqlFieldOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if f.name.isEmpty then
      diags += DiagnosticMessage.error("GqlField name must not be empty", f.span)
    diags.result()
