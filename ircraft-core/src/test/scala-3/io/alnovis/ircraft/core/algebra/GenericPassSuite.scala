package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances.given
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
import io.alnovis.ircraft.core.algebra.TestDialectF._
import io.alnovis.ircraft.core.algebra.TestDialectF.given
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import munit.FunSuite

/** Generic passes that work across any dialect with the right trait constraints. */
object GenericPasses:

  /** Collect all names from a tree. Works on any dialect with HasName + Traverse. */
  def collectAllNames[F[_]: Traverse: HasName]: Fix[F] => Vector[String] =
    scheme.cata[F, Vector[String]] { fa =>
      Vector(HasName[F].name(fa)) ++ Traverse[F].foldLeft(fa, Vector.empty[String])(_ ++ _)
    }

  /** Find nodes with empty names. Returns diagnostics. */
  def validateNoEmptyNames[F[_]: Traverse: HasName]: Fix[F] => Vector[Diagnostic] =
    scheme.cata[F, Vector[Diagnostic]] { fa =>
      val name       = HasName[F].name(fa)
      val childDiags = Traverse[F].foldLeft(fa, Vector.empty[Diagnostic])(_ ++ _)
      if (name.trim.isEmpty)
        childDiags :+ Diagnostic(Severity.Error, s"Empty name found in dialect node")
      else
        childDiags
    }

class GenericPassSuite extends FunSuite {

  import GenericPasses._

  // ---- Step E: collectAllNames ----

  test("collectAllNames on SemanticF tree") {
    val tree = Fix[SemanticF](
      TypeDeclF(
        "Outer",
        TypeKind.Product,
        nested = Vector(
          Fix[SemanticF](TypeDeclF("Inner", TypeKind.Product)),
          Fix[SemanticF](EnumDeclF("Status"))
        )
      )
    )
    assertEquals(collectAllNames[SemanticF].apply(tree).toSet, Set("Outer", "Inner", "Status"))
  }

  test("collectAllNames on TestDialectF tree") {
    val tree = Fix[TestDialectF](
      BranchF(
        "root",
        Vector(
          Fix[TestDialectF](LeafF("a")),
          Fix[TestDialectF](LeafF("b"))
        )
      )
    )
    assertEquals(collectAllNames[TestDialectF].apply(tree).toSet, Set("root", "a", "b"))
  }

  test("collectAllNames on Coproduct tree") {
    type Mixed = TestDialectF :+: SemanticF
    val injT = Inject[TestDialectF, Mixed]
    val injS = Inject[SemanticF, Mixed]

    val tree = Fix[Mixed](
      injT.inj(
        BranchF(
          "top",
          Vector(
            Fix[Mixed](injT.inj(LeafF("x"))),
            Fix[Mixed](injS.inj(TypeDeclF[Fix[Mixed]]("User", TypeKind.Product)))
          )
        )
      )
    )

    val names = collectAllNames[Mixed].apply(tree)
    assertEquals(names.toSet, Set("top", "x", "User"))
  }

  test("collectAllNames on single leaf") {
    val tree = Fix[TestDialectF](LeafF("solo"))
    assertEquals(collectAllNames[TestDialectF].apply(tree), Vector("solo"))
  }

  test("collectAllNames on deep tree") {
    var tree: Fix[TestDialectF] = Fix(LeafF("base"))
    for (i <- 1 to 100) {
      tree = Fix(BranchF(s"n$i", Vector(tree)))
    }
    val names = collectAllNames[TestDialectF].apply(tree)
    assertEquals(names.size, 101) // 100 branches + 1 leaf
    assert(names.contains("base"), "missing base")
    assert(names.contains("n1"), "missing n1")
    assert(names.contains("n100"), "missing n100")
  }

  // ---- Step F: validateNoEmptyNames ----

  test("validateNoEmptyNames returns empty for valid tree") {
    val tree = Fix[SemanticF](TypeDeclF("User", TypeKind.Product, nested = Vector(Fix[SemanticF](EnumDeclF("Status")))))
    assertEquals(validateNoEmptyNames[SemanticF].apply(tree), Vector.empty)
  }

  test("validateNoEmptyNames detects empty name") {
    val tree = Fix[SemanticF](
      TypeDeclF("Outer", TypeKind.Product, nested = Vector(Fix[SemanticF](TypeDeclF("", TypeKind.Product))))
    )
    val diags = validateNoEmptyNames[SemanticF].apply(tree)
    assertEquals(diags.size, 1)
    assert(diags.head.isError, "expected error diagnostic")
    assert(diags.head.message.contains("Empty name"), s"expected 'Empty name' in: ${diags.head.message}")
  }

  test("validateNoEmptyNames detects whitespace-only name") {
    val tree  = Fix[TestDialectF](BranchF("  ", Vector(Fix[TestDialectF](LeafF("ok")))))
    val diags = validateNoEmptyNames[TestDialectF].apply(tree)
    assertEquals(diags.size, 1)
    assert(diags.head.isError, "expected error")
  }

  test("validateNoEmptyNames works on Coproduct") {
    type Mixed = TestDialectF :+: SemanticF
    val injT = Inject[TestDialectF, Mixed]
    val injS = Inject[SemanticF, Mixed]

    val tree = Fix[Mixed](
      injT.inj(
        BranchF(
          "root",
          Vector(
            Fix[Mixed](injS.inj(TypeDeclF[Fix[Mixed]]("", TypeKind.Product)))
          )
        )
      )
    )

    val diags = validateNoEmptyNames[Mixed].apply(tree)
    assertEquals(diags.size, 1)
  }

  test("validateNoEmptyNames returns empty for all-valid Coproduct tree") {
    type Mixed = TestDialectF :+: SemanticF
    val injT = Inject[TestDialectF, Mixed]
    val injS = Inject[SemanticF, Mixed]

    val tree = Fix[Mixed](
      injT.inj(
        BranchF(
          "root",
          Vector(
            Fix[Mixed](injS.inj(TypeDeclF[Fix[Mixed]]("User", TypeKind.Product)))
          )
        )
      )
    )

    assertEquals(validateNoEmptyNames[Mixed].apply(tree), Vector.empty)
  }
}
