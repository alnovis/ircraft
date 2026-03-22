package io.alnovis.ircraft.dialect.semantic

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

class SemanticDialectSuite extends munit.FunSuite:

  test("build a simple interface with methods"):
    val iface = InterfaceOp(
      name = "Money",
      methods = Vector(
        MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract)),
        MethodOp("getCurrency", TypeRef.STRING, modifiers = Set(Modifier.Public, Modifier.Abstract))
      )
    )

    assertEquals(iface.name, "Money")
    assertEquals(iface.methods.size, 2)
    assert(iface.methods(0).isAbstract)
    assert(SemanticDialect.owns(iface))

  test("build a class with fields, constructor, and methods"):
    val cls = ClassOp(
      name = "MoneyV1",
      modifiers = Set(Modifier.Public),
      superClass = Some(TypeRef.NamedType("AbstractMoney")),
      fields = Vector(
        FieldDeclOp("proto", TypeRef.NamedType("MoneyProto"), Set(Modifier.Private, Modifier.Final))
      ),
      constructors = Vector(
        ConstructorOp(
          parameters = List(Parameter("proto", TypeRef.NamedType("MoneyProto"))),
          modifiers = Set(Modifier.Public)
        )
      ),
      methods = Vector(
        MethodOp(
          "getAmount",
          TypeRef.LONG,
          modifiers = Set(Modifier.Public, Modifier.Override),
          body = Some(
            Block.of(
              Statement.ReturnStmt(
                Some(
                  Expression.MethodCall(
                    Some(Expression.Identifier("proto")),
                    "getAmount"
                  )
                )
              )
            )
          )
        )
      )
    )

    assertEquals(cls.name, "MoneyV1")
    assert(!cls.isAbstract)
    assertEquals(cls.fields.size, 1)
    assertEquals(cls.constructors.size, 1)
    assertEquals(cls.methods.size, 1)
    assert(!cls.methods.head.isAbstract)

  test("build an abstract class"):
    val cls = ClassOp(
      name = "AbstractMoney",
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      typeParams = List(TypeParam("PROTO", upperBounds = List(TypeRef.NamedType("Message")))),
      implementsTypes = List(TypeRef.NamedType("Money")),
      methods = Vector(
        MethodOp(
          "extractAmount",
          TypeRef.LONG,
          modifiers = Set(Modifier.Protected, Modifier.Abstract)
        )
      )
    )

    assert(cls.isAbstract)
    assertEquals(cls.typeParams.size, 1)
    assertEquals(cls.implementsTypes.size, 1)
    assert(cls.methods.head.isAbstract)

  test("build an enum class"):
    val enumCls = EnumClassOp(
      name = "Currency",
      constants = Vector(
        EnumConstantOp("USD", List(Expression.Literal("0", TypeRef.INT))),
        EnumConstantOp("EUR", List(Expression.Literal("1", TypeRef.INT)))
      ),
      methods = Vector(
        MethodOp("getValue", TypeRef.INT, modifiers = Set(Modifier.Public))
      )
    )

    assertEquals(enumCls.name, "Currency")
    assertEquals(enumCls.constants.size, 2)
    assertEquals(enumCls.methods.size, 1)

  test("build a FileOp containing interface and class"):
    val file = FileOp(
      packageName = "com.example.api",
      types = Vector(
        InterfaceOp(
          "Money",
          methods = Vector(
            MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract))
          )
        ),
        ClassOp("AbstractMoney", modifiers = Set(Modifier.Public, Modifier.Abstract))
      )
    )

    assertEquals(file.packageName, "com.example.api")
    assertEquals(file.types.size, 2)

  test("expression AST construction"):
    val expr = Expression.MethodCall(
      receiver = Some(Expression.FieldAccess(Expression.ThisRef, "proto")),
      name = "getAmount"
    )

    expr match
      case Expression.MethodCall(Some(Expression.FieldAccess(Expression.ThisRef, "proto")), "getAmount", Nil, Nil) =>
        () // pattern match succeeded
      case _ => fail("Expression pattern match failed")

  test("statement block construction"):
    val block = Block.of(
      Statement.VarDecl("result", TypeRef.LONG, Some(Expression.Literal("0", TypeRef.LONG)), isFinal = true),
      Statement.ReturnStmt(Some(Expression.Identifier("result")))
    )

    assertEquals(block.statements.size, 2)

  test("content hash is deterministic"):
    val m1 = MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract))
    val m2 = MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract))
    assertEquals(m1.contentHash, m2.contentHash)

  test("content hash differs for different methods"):
    val m1 = MethodOp("getAmount", TypeRef.LONG)
    val m2 = MethodOp("getCurrency", TypeRef.STRING)
    assertNotEquals(m1.contentHash, m2.contentHash)

  test("nested types in interface"):
    val iface = InterfaceOp(
      name = "Order",
      nestedTypes = Vector(
        EnumClassOp(
          "Status",
          constants = Vector(
            EnumConstantOp("PENDING"),
            EnumConstantOp("COMPLETED")
          )
        )
      )
    )

    assertEquals(iface.nestedTypes.size, 1)

  test("method with default body"):
    val method = MethodOp(
      "hasAmount",
      TypeRef.BOOL,
      modifiers = Set(Modifier.Public, Modifier.Default),
      body = Some(Block.of(Statement.ReturnStmt(Some(Expression.Literal("true", TypeRef.BOOL)))))
    )

    assert(method.isDefault)
    assert(!method.isAbstract)

  test("SemanticDialect owns semantic operations"):
    val file   = FileOp("com.example")
    val iface  = InterfaceOp("Foo")
    val cls    = ClassOp("Bar")
    val method = MethodOp("baz", TypeRef.VOID)
    val field  = FieldDeclOp("x", TypeRef.INT)

    assert(SemanticDialect.owns(file))
    assert(SemanticDialect.owns(iface))
    assert(SemanticDialect.owns(cls))
    assert(SemanticDialect.owns(method))
    assert(SemanticDialect.owns(field))
