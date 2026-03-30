package io.alnovis.ircraft.dialect.graphql

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.ops.*
import io.alnovis.ircraft.dialect.graphql.passes.GraphQlVerifierPass

class GraphQlVerifierSuite extends FunSuite:

  private def verify(ops: Operation*): List[DiagnosticMessage] =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    GraphQlVerifierPass.run(module, PassContext()).diagnostics

  test("valid schema passes verification"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(
          GqlFieldOp("hello", TypeRef.STRING)
        )),
        EnumTypeOp("Status", values = Vector(GqlEnumValueOp("ACTIVE"))),
        UnionTypeOp("Result", List("A", "B")),
        InputObjectTypeOp("Input", fields = Vector(InputFieldOp("x", TypeRef.INT)))
      )
    )
    val diags = verify(schema)
    assert(diags.forall(!_.isError), s"Expected no errors but got: $diags")

  test("empty schema queryType is error"):
    val schema = GqlSchemaOp(
      "",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(GqlFieldOp("hello", TypeRef.STRING)))
      )
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("queryType must not be empty")))

  test("empty object type name is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("", fields = Vector(GqlFieldOp("hello", TypeRef.STRING)))
      )
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("ObjectType name must not be empty")))

  test("object type with no fields is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(ObjectTypeOp("Empty"))
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("must have at least one field")))

  test("input object type with no fields is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(InputObjectTypeOp("EmptyInput"))
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("InputObjectType") && d.message.contains("must have at least one field")))

  test("interface type with no fields is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(InterfaceTypeOp("EmptyInterface"))
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("InterfaceType") && d.message.contains("must have at least one field")))

  test("enum type with no values is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(EnumTypeOp("EmptyEnum"))
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("must have at least one value")))

  test("union type with no members is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(UnionTypeOp("EmptyUnion", Nil))
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("must have at least one member")))

  test("empty field name is error"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(GqlFieldOp("", TypeRef.STRING)))
      )
    )
    val diags = verify(schema)
    assert(diags.exists(d => d.isError && d.message.contains("GqlField name must not be empty")))
