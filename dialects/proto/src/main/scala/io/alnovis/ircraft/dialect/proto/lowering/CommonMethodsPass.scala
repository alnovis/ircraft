package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/**
  * Adds common utility methods to abstract classes: equals, hashCode, toString, serialization.
  *
  * Adds to abstract class:
  *   - `equals(Object)` — compare versionId + proto
  *   - `hashCode()` — hash of versionId + proto
  *   - `toString()` — includes wrapper version + proto content
  *   - `serializeToBytes()` — abstract, implemented by version impls
  *   - `toBytes()` — delegates to serializeToBytes()
  *   - `getTypedProto()` — returns proto field
  *   - `getWrapperVersionId()` — abstract, implemented by version impls
  */
object CommonMethodsPass extends Pass:

  val name: String        = "common-methods"
  val description: String = "Adds equals, hashCode, toString, serialization to abstract classes"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case cls: ClassOp if cls.isAbstract && cls.attributes.contains(ProtoAttributes.PresentInVersions) =>
        enrichAbstractClass(cls)
    PassResult(transformed)

  private def enrichAbstractClass(cls: ClassOp): ClassOp =
    val methods = Vector.newBuilder[MethodOp]

    // getTypedProto() → returns proto
    methods += MethodOp(
      "getTypedProto",
      TypeRef.NamedType("com.google.protobuf.Message"),
      modifiers = Set(Modifier.Public, Modifier.Override),
      body = Some(
        Block.of(
          Statement.ReturnStmt(Some(Expression.Identifier("proto")))
        )
      )
    )

    // getWrapperVersionId() → abstract
    methods += MethodOp(
      "getWrapperVersionId",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public, Modifier.Abstract)
    )

    // serializeToBytes() → abstract template method
    methods += MethodOp(
      "serializeToBytes",
      TypeRef.BYTES,
      modifiers = Set(Modifier.Protected, Modifier.Abstract)
    )

    // toBytes() → delegates to serializeToBytes()
    methods += MethodOp(
      "toBytes",
      TypeRef.BYTES,
      modifiers = Set(Modifier.Public, Modifier.Override),
      body = Some(
        Block.of(
          Statement.ReturnStmt(Some(Expression.MethodCall(None, "serializeToBytes")))
        )
      )
    )

    // toString()
    methods += MethodOp(
      "toString",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public, Modifier.Override),
      annotations = List("Override"),
      body = Some(
        Block.of(
          Statement.ReturnStmt(
            Some(
              Expression.BinaryOp(
                Expression.BinaryOp(
                  Expression.BinaryOp(
                    Expression.Literal(s""""${cls.name}["""", TypeRef.STRING),
                    BinOperator.Add,
                    Expression.MethodCall(None, "getWrapperVersionId")
                  ),
                  BinOperator.Add,
                  Expression.Literal(""""] """", TypeRef.STRING)
                ),
                BinOperator.Add,
                Expression.MethodCall(Some(Expression.Identifier("proto")), "toString")
              )
            )
          )
        )
      )
    )

    // equals(Object)
    methods += MethodOp(
      "equals",
      TypeRef.BOOL,
      parameters = List(Parameter("obj", TypeRef.NamedType("Object"))),
      modifiers = Set(Modifier.Public, Modifier.Override),
      annotations = List("Override"),
      body = Some(
        Block.of(
          Statement.IfStmt(
            Expression.BinaryOp(Expression.ThisRef, BinOperator.Eq, Expression.Identifier("obj")),
            Block.of(Statement.ReturnStmt(Some(Expression.Literal("true", TypeRef.BOOL)))),
            None
          ),
          Statement.IfStmt(
            Expression.BinaryOp(Expression.Identifier("obj"), BinOperator.Eq, Expression.NullLiteral),
            Block.of(Statement.ReturnStmt(Some(Expression.Literal("false", TypeRef.BOOL)))),
            None
          ),
          Statement.ReturnStmt(
            Some(
              Expression.BinaryOp(
                Expression.MethodCall(
                  Some(Expression.MethodCall(None, "getWrapperVersionId")),
                  "equals",
                  List(
                    Expression.MethodCall(
                      Some(Expression.Cast(Expression.Identifier("obj"), TypeRef.NamedType(cls.name))),
                      "getWrapperVersionId"
                    )
                  )
                ),
                BinOperator.And,
                Expression.MethodCall(
                  Some(Expression.Identifier("proto")),
                  "equals",
                  List(
                    Expression.FieldAccess(
                      Expression.Cast(Expression.Identifier("obj"), TypeRef.NamedType(cls.name)),
                      "proto"
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    // hashCode()
    methods += MethodOp(
      "hashCode",
      TypeRef.INT,
      modifiers = Set(Modifier.Public, Modifier.Override),
      annotations = List("Override"),
      body = Some(
        Block.of(
          Statement.ReturnStmt(
            Some(
              Expression.BinaryOp(
                Expression.BinaryOp(
                  Expression.Literal("31", TypeRef.INT),
                  BinOperator.Mul,
                  Expression.MethodCall(
                    Some(Expression.MethodCall(None, "getWrapperVersionId")),
                    "hashCode"
                  )
                ),
                BinOperator.Add,
                Expression.MethodCall(Some(Expression.Identifier("proto")), "hashCode")
              )
            )
          )
        )
      )
    )

    ClassOp(
      name = cls.name,
      modifiers = cls.modifiers,
      typeParams = cls.typeParams,
      superClass = cls.superClass,
      implementsTypes = cls.implementsTypes,
      fields = cls.fields,
      constructors = cls.constructors,
      methods = cls.methods ++ methods.result(),
      nestedTypes = cls.nestedTypes,
      javadoc = cls.javadoc,
      annotations = cls.annotations,
      attributes = cls.attributes,
      span = cls.span
    )
