package io.alnovis.ircraft.dialect.graphql

import io.alnovis.ircraft.core.*

/** GraphQL schema dialect -- represents a GraphQL schema definition. */
object GraphQlDialect extends Dialect:

  val namespace: String   = "graphql"
  val description: String = "GraphQL schema representation"

  object Kinds:
    val Schema: NodeKind          = NodeKind(namespace, "schema")
    val ObjectType: NodeKind      = NodeKind(namespace, "object_type")
    val InputObjectType: NodeKind = NodeKind(namespace, "input_object_type")
    val InterfaceType: NodeKind   = NodeKind(namespace, "interface_type")
    val UnionType: NodeKind       = NodeKind(namespace, "union_type")
    val EnumType: NodeKind        = NodeKind(namespace, "enum_type")
    val ScalarType: NodeKind      = NodeKind(namespace, "scalar_type")
    val DirectiveDef: NodeKind    = NodeKind(namespace, "directive_def")
    val GqlField: NodeKind        = NodeKind(namespace, "gql_field")
    val InputField: NodeKind      = NodeKind(namespace, "input_field")
    val GqlArgument: NodeKind     = NodeKind(namespace, "gql_argument")
    val GqlEnumValue: NodeKind    = NodeKind(namespace, "gql_enum_value")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.Schema, Kinds.ObjectType, Kinds.InputObjectType, Kinds.InterfaceType,
    Kinds.UnionType, Kinds.EnumType, Kinds.ScalarType, Kinds.DirectiveDef,
    Kinds.GqlField, Kinds.InputField, Kinds.GqlArgument, Kinds.GqlEnumValue
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to graphql dialect"))
    else Nil
