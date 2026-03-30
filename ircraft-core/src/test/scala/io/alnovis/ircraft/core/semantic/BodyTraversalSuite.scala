package io.alnovis.ircraft.core.semantic

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.expr.*
import io.alnovis.ircraft.core.semantic.ops.*
import io.alnovis.ircraft.core.semantic.BodyTraversal.*

class BodyTraversalSuite extends munit.FunSuite:

  val moduleWithBodies: IrModule =
    val method1 = MethodOp(
      "getAmount",
      TypeRef.LONG,
      modifiers = Set(Modifier.Public),
      body = Some(
        Block.of(
          Statement.ReturnStmt(
            Some(
              Expression.MethodCall(Some(Expression.Identifier("proto")), "getAmount")
            )
          )
        )
      )
    )
    val method2 = MethodOp(
      "getCurrency",
      TypeRef.STRING,
      modifiers = Set(Modifier.Public),
      body = Some(
        Block.of(
          Statement.ReturnStmt(
            Some(
              Expression.MethodCall(Some(Expression.Identifier("proto")), "getCurrency")
            )
          )
        )
      )
    )
    val constructor = ConstructorOp(
      parameters = List(Parameter("proto", TypeRef.NamedType("Proto"))),
      body = Some(
        Block.of(
          Statement.Assignment(
            Expression.FieldAccess(Expression.ThisRef, "proto"),
            Expression.Identifier("proto")
          )
        )
      )
    )
    val cls = ClassOp(
      name = "MoneyV1",
      fields = Vector(FieldDeclOp("proto", TypeRef.NamedType("Proto"))),
      constructors = Vector(constructor),
      methods = Vector(method1, method2)
    )
    IrModule("test", Vector(FileOp("com.example", Vector(cls))))

  test("collectFromBodies finds all MethodCalls"):
    val calls = moduleWithBodies.collectFromBodies {
      case Expression.MethodCall(_, name, _, _) => name
    }
    assertEquals(calls.sorted, Vector("getAmount", "getCurrency"))

  test("collectFromBodies finds Identifiers in constructor"):
    val ids = moduleWithBodies.collectFromBodies {
      case Expression.Identifier(n) => n
    }
    assert(ids.contains("proto"))

  test("collectFromBodies returns empty for module without bodies"):
    val abstractMethod = MethodOp("foo", TypeRef.VOID, modifiers = Set(Modifier.Public, Modifier.Abstract))
    val iface          = InterfaceOp("Foo", methods = Vector(abstractMethod))
    val module         = IrModule("test", Vector(FileOp("com.example", Vector(iface))))
    val calls          = module.collectFromBodies { case Expression.MethodCall(_, n, _, _) => n }
    assertEquals(calls, Vector.empty)

  test("transformBodies replaces Identifier across all bodies"):
    val transformed = moduleWithBodies.transformBodies {
      case Expression.Identifier("proto") => Expression.Identifier("delegate")
    }
    val ids = transformed.collectFromBodies { case Expression.Identifier(n) => n }
    assert(!ids.contains("proto"), s"Should not contain 'proto', got: $ids")
    assert(ids.contains("delegate"), s"Should contain 'delegate', got: $ids")

  test("transformBodies preserves structure"):
    val transformed = moduleWithBodies.transformBodies {
      case Expression.Identifier("proto") => Expression.Identifier("x")
    }
    val files = transformed.collect { case f: FileOp => f }
    assertEquals(files.size, 1)
    val cls = files.head.types.head.asInstanceOf[ClassOp]
    assertEquals(cls.name, "MoneyV1")
    assertEquals(cls.methods.size, 2)
    assertEquals(cls.constructors.size, 1)
