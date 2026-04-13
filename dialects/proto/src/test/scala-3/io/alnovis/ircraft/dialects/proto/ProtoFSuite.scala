package io.alnovis.ircraft.dialects.proto

import cats.{ Applicative, Functor, Id, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances.given
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.dialects.proto.ProtoF._
import io.alnovis.ircraft.dialects.proto.ProtoF.given
import io.alnovis.ircraft.dialects.proto.ProtoEliminate._
import munit.FunSuite

class ProtoFSuite extends FunSuite {

  // ---- Step A: Functor/Traverse ----

  test("Functor map identity on MessageNodeF") {
    val msg: ProtoF[Int] = MessageNodeF("User", nested = Vector(1, 2))
    assertEquals(Functor[ProtoF].map(msg)(identity), msg)
  }

  test("Functor map on MessageNodeF transforms nested") {
    val msg: ProtoF[Int] = MessageNodeF("User", nested = Vector(1, 2, 3))
    assertEquals(Functor[ProtoF].map(msg)(_ * 10), MessageNodeF("User", nested = Vector(10, 20, 30)))
  }

  test("Functor map on EnumNodeF copies as-is") {
    val e: ProtoF[Int] = EnumNodeF("Status", variants = Vector(EnumVariant("OK")))
    assertEquals(Functor[ProtoF].map(e)(_ * 2), EnumNodeF("Status", variants = Vector(EnumVariant("OK"))))
  }

  test("Traverse on MessageNodeF sequences nested") {
    val msg: ProtoF[Int] = MessageNodeF("X", nested = Vector(1, 2))
    val result           = Traverse[ProtoF].traverse(msg)(x => Option(x.toString))
    assertEquals(result, Some(MessageNodeF("X", nested = Vector("1", "2"))))
  }

  test("foldLeft on MessageNodeF sums nested") {
    val msg: ProtoF[Int] = MessageNodeF("X", nested = Vector(1, 2, 3))
    assertEquals(Traverse[ProtoF].foldLeft(msg, 0)(_ + _), 6)
  }

  test("foldLeft on EnumNodeF returns initial") {
    assertEquals(Traverse[ProtoF].foldLeft(EnumNodeF[Int]("X"), 0)(_ + _), 0)
  }

  test("foldLeft on OneofNodeF returns initial") {
    assertEquals(Traverse[ProtoF].foldLeft(OneofNodeF[Int]("X"), 0)(_ + _), 0)
  }

  // ---- Step B: DialectInfo + HasName + HasNested ----

  test("DialectInfo[ProtoF]") {
    assertEquals(DialectInfo[ProtoF].dialectName, "ProtoF")
    assertEquals(DialectInfo[ProtoF].operationCount, 3)
  }

  test("HasName on MessageNodeF") {
    assertEquals(HasName[ProtoF].name(MessageNodeF("User")), "User")
  }

  test("HasName on EnumNodeF") {
    assertEquals(HasName[ProtoF].name(EnumNodeF("Status")), "Status")
  }

  test("HasName on OneofNodeF") {
    assertEquals(HasName[ProtoF].name(OneofNodeF("Value")), "Value")
  }

  test("HasNested on MessageNodeF returns nested") {
    assertEquals(HasNested[ProtoF].nested(MessageNodeF("X", nested = Vector(1, 2))), Vector(1, 2))
  }

  test("HasNested on EnumNodeF returns empty") {
    assertEquals(HasNested[ProtoF].nested(EnumNodeF[Int]("X")), Vector.empty[Int])
  }

  // ---- Step C: ProtoIR + lowering algebra + eliminateProto ----

  val injP = Inject[ProtoF, ProtoIR]
  val injS = Inject[SemanticF, ProtoIR]

  test("protoToSemantic converts MessageNodeF to TypeDeclF(Protocol)") {
    val result = ProtoDialect.protoToSemantic(
      MessageNodeF(
        "User",
        fields = Vector(Field("id", TypeExpr.LONG)),
        nested = Vector(Fix[SemanticF](TypeDeclF("Inner", TypeKind.Product)))
      )
    )
    result.unfix match
      case TypeDeclF(name, kind, fields, _, nested, _, _, _, _, _) =>
        assertEquals(name, "User")
        assertEquals(kind, TypeKind.Protocol)
        assertEquals(fields.size, 1)
        assertEquals(nested.size, 1)
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("protoToSemantic converts EnumNodeF to EnumDeclF") {
    val result = ProtoDialect.protoToSemantic(
      EnumNodeF("Status", variants = Vector(EnumVariant("OK"), EnumVariant("ERROR")))
    )
    result.unfix match
      case EnumDeclF(name, variants, _, _, _, _, _) =>
        assertEquals(name, "Status")
        assertEquals(variants.size, 2)
      case other => fail(s"expected EnumDeclF, got $other")
  }

  test("protoToSemantic converts OneofNodeF to TypeDeclF(Sum)") {
    val result = ProtoDialect.protoToSemantic(
      OneofNodeF("Value", fields = Vector(Field("strVal", TypeExpr.STR), Field("intVal", TypeExpr.INT)))
    )
    result.unfix match
      case TypeDeclF(name, kind, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "Value")
        assertEquals(kind, TypeKind.Sum)
        assertEquals(fields.size, 2)
      case other => fail(s"expected TypeDeclF(Sum), got $other")
  }

  test("eliminateProto converts mixed tree to pure SemanticF") {
    val mixed = Fix[ProtoIR](
      injP.inj(
        MessageNodeF(
          "Root",
          nested = Vector(
            Fix[ProtoIR](injP.inj(EnumNodeF("Status", variants = Vector(EnumVariant("OK"))))),
            Fix[ProtoIR](injS.inj(TypeDeclF[Fix[ProtoIR]]("Existing", TypeKind.Product)))
          )
        )
      )
    )

    val clean: Fix[SemanticF] = eliminateProto(mixed)
    clean.unfix match
      case TypeDeclF(name, kind, _, _, nested, _, _, _, _, _) =>
        assertEquals(name, "Root")
        assertEquals(kind, TypeKind.Protocol)
        assertEquals(nested.size, 2)
        assertEquals(SemanticF.name(nested(0).unfix), "Status")
        assertEquals(SemanticF.name(nested(1).unfix), "Existing")
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("eliminateProto preserves SemanticF nodes") {
    val mixed = Fix[ProtoIR](
      injS.inj(
        TypeDeclF[Fix[ProtoIR]]("Passthrough", TypeKind.Product, fields = Vector(Field("x", TypeExpr.STR)))
      )
    )
    val clean = eliminateProto(mixed)
    clean.unfix match
      case TypeDeclF(name, _, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "Passthrough")
        assertEquals(fields.size, 1)
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("collectAllNames works on ProtoIR coproduct") {
    val mixed = Fix[ProtoIR](
      injP.inj(
        MessageNodeF(
          "Root",
          nested = Vector(
            Fix[ProtoIR](injP.inj(EnumNodeF("Color"))),
            Fix[ProtoIR](injS.inj(TypeDeclF[Fix[ProtoIR]]("User", TypeKind.Product)))
          )
        )
      )
    )

    def collectAllNames[F[_]: Traverse: HasName]: Fix[F] => Vector[String] =
      scheme.cata[F, Vector[String]] { fa =>
        Vector(HasName[F].name(fa)) ++ Traverse[F].foldLeft(fa, Vector.empty[String])(_ ++ _)
      }

    val names = collectAllNames[ProtoIR].apply(mixed)
    assertEquals(names.toSet, Set("Root", "Color", "User"))
  }

  // ---- Step D+E: E2E pipeline ----

  val testFile = ProtoFile(
    name = "test.proto",
    syntax = ProtoSyntax.Proto3,
    packageName = "com.example",
    javaPackage = Some("com.example.proto"),
    javaOuterClassname = None,
    javaMultipleFiles = true,
    messages = Vector(
      ProtoMessage(
        name = "User",
        fields = Vector(
          ProtoField("id", 1, ProtoType.Int64, ProtoLabel.Required, None),
          ProtoField("name", 2, ProtoType.String, ProtoLabel.Required, None)
        ),
        nestedMessages = Vector.empty,
        nestedEnums = Vector.empty,
        oneofs = Vector.empty
      )
    ),
    enums = Vector(
      ProtoEnum("Status", Vector(ProtoEnumValue("ACTIVE", 0), ProtoEnumValue("DELETED", 1)))
    )
  )

  test("lowerToMixed produces Module[Fix[ProtoIR]]") {
    val module = ProtoLowering.lowerToMixed[Id, ProtoIR](testFile)
    assertEquals(module.units.size, 1)
    assertEquals(module.units.head.declarations.size, 2) // User + Status
  }

  test("E2E: lowerToMixed -> eliminateProto produces valid SemanticF") {
    val mixed = ProtoLowering.lowerToMixed[Id, ProtoIR](testFile)
    val clean = mixed.mapDecls(eliminateProto)

    assertEquals(clean.units.size, 1)
    val decls = clean.units.head.declarations
    assertEquals(decls.size, 2)

    // User -> TypeDeclF(Protocol)
    decls(0).unfix match
      case TypeDeclF(name, kind, _, _, _, _, _, _, _, _) =>
        assertEquals(name, "User")
        assertEquals(kind, TypeKind.Protocol)
      case other => fail(s"expected TypeDeclF for User, got $other")

    // Status -> EnumDeclF
    decls(1).unfix match
      case EnumDeclF(name, variants, _, _, _, _, _) =>
        assertEquals(name, "Status")
        assertEquals(variants.size, 2)
      case other => fail(s"expected EnumDeclF for Status, got $other")
  }

  test("E2E: mixed path equivalent to direct path") {
    // Direct path
    val direct = ProtoLowering.lower[Id](testFile)

    // Mixed path
    val mixed = ProtoLowering.lowerToMixed[Id, ProtoIR](testFile)
    val clean = mixed.mapDecls(eliminateProto)

    // Same number of declarations
    assertEquals(direct.units.head.declarations.size, clean.units.head.declarations.size)

    // Same names
    val directNames = direct.units.head.declarations.map(d => SemanticF.name(d.unfix))
    val cleanNames  = clean.units.head.declarations.map(d => SemanticF.name(d.unfix))
    assertEquals(directNames, cleanNames)
  }
}
