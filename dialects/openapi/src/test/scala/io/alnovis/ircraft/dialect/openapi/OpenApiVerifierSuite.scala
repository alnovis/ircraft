package io.alnovis.ircraft.dialect.openapi

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.ops.*
import io.alnovis.ircraft.dialect.openapi.passes.OpenApiVerifierPass

class OpenApiVerifierSuite extends FunSuite:

  private def verify(ops: Operation*): List[DiagnosticMessage] =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    OpenApiVerifierPass.run(module, PassContext()).diagnostics

  test("valid spec passes verification"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaObjectOp("User", properties = Vector(
          SchemaPropertyOp("id", TypeRef.INT, required = true)
        ))
      ),
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(HttpMethod.Get, operationId = Some("listUsers"),
            responses = Vector(ResponseOp("200", "OK")))
        ))
      )
    )
    val diags = verify(spec)
    assert(diags.forall(!_.isError), s"Expected no errors but got: $diags")

  test("empty schema object name is error"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(SchemaObjectOp(""))
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("name must not be empty")))

  test("empty enum values is error"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(SchemaEnumOp("Status", TypeRef.STRING))
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("must have at least one value")))

  test("empty enum name is error"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(SchemaEnumOp("", TypeRef.STRING, values = Vector(
        SchemaEnumValueOp("active", "active")
      )))
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("enum name must not be empty")))

  test("operation without responses is error"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(HttpMethod.Get, operationId = Some("listUsers"))
        ))
      )
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("must have at least one response")))

  test("path parameter must be required"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users/{id}", operations = Vector(
          OperationOp(
            HttpMethod.Get,
            operationId = Some("getUser"),
            parameters = Vector(
              ParameterOp("id", ParameterLocation.Path, TypeRef.STRING, required = false)
            ),
            responses = Vector(ResponseOp("200", "OK"))
          )
        ))
      )
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("must be required")))

  test("empty path pattern is error"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("", operations = Vector(
          OperationOp(HttpMethod.Get,
            responses = Vector(ResponseOp("200", "OK")))
        ))
      )
    )
    val diags = verify(spec)
    assert(diags.exists(d => d.isError && d.message.contains("Path pattern must not be empty")))

  test("query parameter not required is allowed"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(
            HttpMethod.Get,
            operationId = Some("listUsers"),
            parameters = Vector(
              ParameterOp("limit", ParameterLocation.Query, TypeRef.INT, required = false)
            ),
            responses = Vector(ResponseOp("200", "OK"))
          )
        ))
      )
    )
    val diags = verify(spec)
    assert(diags.forall(!_.isError), s"Expected no errors but got: $diags")
