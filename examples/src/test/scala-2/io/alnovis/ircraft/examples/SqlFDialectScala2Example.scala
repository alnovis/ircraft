package io.alnovis.ircraft.examples

import cats.{Applicative, Eval, Id, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances._
import io.alnovis.ircraft.core.algebra.CoproductInstances._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.emitters.java.JavaEmitter

// ============================================================
// SqlF for Scala 2: sealed trait + manual instances
// Proves the same approach works on 2.12/2.13
// ============================================================

sealed trait SqlF2[+A]

object SqlF2 {
  final case class TableNodeF[+A](
    name: String,
    columns: Vector[Field],
    constraints: Vector[String] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF2[A]

  final case class ViewNodeF[+A](
    name: String,
    query: String,
    columns: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF2[A]

  final case class IndexNodeF[+A](
    name: String,
    tableName: String,
    columnNames: Vector[String],
    meta: Meta = Meta.empty
  ) extends SqlF2[A]

  // Traverse (extends Functor)
  implicit val sqlTraverse: Traverse[SqlF2] = new Traverse[SqlF2] {
    def traverse[G[_], A, B](fa: SqlF2[A])(f: A => G[B])(implicit G: Applicative[G]): G[SqlF2[B]] = fa match {
      case TableNodeF(n, cols, cons, nested, m) =>
        nested.traverse(f).map(ns => TableNodeF[B](n, cols, cons, ns, m))
      case ViewNodeF(n, q, cols, m) =>
        G.pure(ViewNodeF[B](n, q, cols, m))
      case IndexNodeF(n, t, cols, m) =>
        G.pure(IndexNodeF[B](n, t, cols, m))
    }

    def foldLeft[A, B](fa: SqlF2[A], b: B)(f: (B, A) => B): B = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                              => b
    }

    def foldRight[A, B](fa: SqlF2[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                              => lb
    }
  }

  implicit val sqlDialectInfo: DialectInfo[SqlF2] = DialectInfo("SqlF2", 3)

  implicit val sqlHasName: HasName[SqlF2] = new HasName[SqlF2] {
    def name[A](fa: SqlF2[A]): String = fa match {
      case TableNodeF(n, _, _, _, _) => n
      case ViewNodeF(n, _, _, _)     => n
      case IndexNodeF(n, _, _, _)    => n
    }
  }

  implicit val sqlHasNested: HasNested[SqlF2] = new HasNested[SqlF2] {
    def nested[A](fa: SqlF2[A]): Vector[A] = fa match {
      case TableNodeF(_, _, _, nested, _) => nested
      case _                              => Vector.empty
    }
  }
}

object SqlDialect2 {
  import SqlF2._

  val sqlQueryKey: Meta.Key[String] = Meta.Key("sql.query")
  val sqlIndexTableKey: Meta.Key[String] = Meta.Key("sql.indexTable")
  val sqlIndexColumnsKey: Meta.Key[Vector[String]] = Meta.Key("sql.indexColumns")

  val sqlToSemantic: Algebra[SqlF2, Fix[SemanticF]] = {
    case TableNodeF(name, columns, constraints, nested, meta) =>
      val annotations = constraints.map(c => Annotation(c))
      Fix[SemanticF](TypeDeclF(name, TypeKind.Product, fields = columns,
        nested = nested, annotations = annotations, meta = meta))
    case ViewNodeF(name, query, columns, meta) =>
      Fix[SemanticF](TypeDeclF(name, TypeKind.Protocol, fields = columns,
        meta = meta.set(sqlQueryKey, query)))
    case IndexNodeF(name, tableName, columnNames, meta) =>
      Fix[SemanticF](ConstDeclF(name, TypeExpr.STR,
        Expr.Lit(s"$tableName(${columnNames.mkString(",")})", TypeExpr.STR),
        meta = meta.set(sqlIndexTableKey, tableName).set(sqlIndexColumnsKey, columnNames)))
  }

  // On Scala 2: Coproduct[SqlF2, SemanticF, *] via kind-projector
  val eliminateSql: Fix[Coproduct[SqlF2, SemanticF, *]] => Fix[SemanticF] =
    eliminate.dialect(sqlToSemantic)
}


class SqlFDialectScala2Example extends munit.FunSuite {

  import SqlF2._
  import SqlDialect2._

  val injS: Inject[SqlF2, Coproduct[SqlF2, SemanticF, *]] =
    Inject.left[SqlF2, SemanticF]
  val injSem: Inject[SemanticF, Coproduct[SqlF2, SemanticF, *]] =
    Inject.right[SemanticF, SemanticF, SqlF2]

  // ---- Functor/Traverse ----

  test("Functor map identity on TableNodeF (Scala 2)") {
    val t: SqlF2[Int] = TableNodeF("users", Vector.empty, nested = Vector(1, 2))
    assertEquals(Traverse[SqlF2].map(t)(identity), t)
  }

  test("Traverse on TableNodeF (Scala 2)") {
    val t: SqlF2[Int] = TableNodeF("users", Vector.empty, nested = Vector(1, 2, 3))
    val result = Traverse[SqlF2].traverse(t)(x => Option(x.toString))
    assertEquals(result, Some(TableNodeF("users", Vector.empty, nested = Vector("1", "2", "3"))))
  }

  test("HasName (Scala 2)") {
    assertEquals(HasName[SqlF2].name(TableNodeF("users", Vector.empty)), "users")
    assertEquals(HasName[SqlF2].name(ViewNodeF("v", "SELECT 1")), "v")
  }

  test("DialectInfo (Scala 2)") {
    assertEquals(DialectInfo[SqlF2].dialectName, "SqlF2")
    assertEquals(DialectInfo[SqlF2].operationCount, 3)
  }

  // ---- Lowering algebra ----

  test("sqlToSemantic converts TableNodeF (Scala 2)") {
    val cols = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR))
    val result = sqlToSemantic(TableNodeF("users", cols))
    result.unfix match {
      case TypeDeclF(name, kind, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "users")
        assertEquals(kind, TypeKind.Product)
        assertEquals(fields.size, 2)
      case other => fail(s"expected TypeDeclF, got $other")
    }
  }

  // ---- E2E pipeline ----

  test("E2E: SqlF2 -> eliminate -> emit Java (Scala 2)") {
    type IR[A] = Coproduct[SqlF2, SemanticF, A]

    val table = Fix[IR](injS.inj(TableNodeF[Fix[IR]](
      "User",
      columns = Vector(
        Field("id", TypeExpr.LONG, mutability = Mutability.Mutable),
        Field("name", TypeExpr.STR, mutability = Mutability.Mutable)
      )
    )))

    val clean = eliminateSql(table)
    val module = Module("sql-demo", Vector(
      CompilationUnit("com.example.model", Vector(clean))
    ))

    val files = new JavaEmitter[Id].apply(module)
    assertEquals(files.size, 1)

    val source = files.values.head
    assert(source.contains("public class User"), s"expected class User in: $source")
    assert(source.contains("public long id;"), s"expected id field in: $source")
    assert(source.contains("public String name;"), s"expected name field in: $source")
  }

  // ---- Generic pass on Coproduct (Scala 2) ----

  test("collectAllNames on Coproduct SqlF2 :+: SemanticF (Scala 2)") {
    type IR[A] = Coproduct[SqlF2, SemanticF, A]

    val tree = Fix[IR](injS.inj(TableNodeF[Fix[IR]](
      "root",
      columns = Vector.empty,
      nested = Vector(
        Fix[IR](injSem.inj(TypeDeclF[Fix[IR]]("Inner", TypeKind.Product)))
      )
    )))

    def collectNames[F[_]](implicit T: Traverse[F], N: HasName[F]): Fix[F] => Vector[String] =
      scheme.cata[F, Vector[String]] { fa =>
        Vector(N.name(fa)) ++ T.foldLeft(fa, Vector.empty[String])(_ ++ _)
      }

    val names = collectNames[IR].apply(tree)
    assertEquals(names.toSet, Set("root", "Inner"))
  }
}
