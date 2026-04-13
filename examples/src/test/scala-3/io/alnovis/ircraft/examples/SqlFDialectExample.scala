package io.alnovis.ircraft.examples

import cats.{ Applicative, Eval, Id, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances.given
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.emitters.java.JavaEmitter
import munit.FunSuite

// ============================================================
// SqlF: Community dialect defined entirely in examples/
// Zero changes to ircraft-core, ircraft-emit, or emitters/java
// ============================================================

/** SQL dialect functor -- represents SQL DDL constructs. */
enum SqlF[+A]:

  case TableNodeF(
    name: String,
    columns: Vector[Field],
    constraints: Vector[String] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  case ViewNodeF(
    name: String,
    query: String,
    columns: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  case IndexNodeF(
    name: String,
    tableName: String,
    columnNames: Vector[String],
    meta: Meta = Meta.empty
  ) extends SqlF[A]

object SqlF:
  import SqlF._

  // Template Rule: Traverse (extends Functor)
  // TableNodeF.nested: Vector[A] -- recursive
  // ViewNodeF, IndexNodeF -- data-only (no A fields)

  given Traverse[SqlF] with

    def traverse[G[_]: Applicative, A, B](fa: SqlF[A])(f: A => G[B]): G[SqlF[B]] = fa match
      case TableNodeF(n, cols, cons, nested, m) =>
        nested.traverse(f).map(ns => TableNodeF(n, cols, cons, ns, m))
      case ViewNodeF(n, q, cols, m) =>
        Applicative[G].pure(ViewNodeF(n, q, cols, m))
      case IndexNodeF(n, t, cols, m) =>
        Applicative[G].pure(IndexNodeF(n, t, cols, m))

    def foldLeft[A, B](fa: SqlF[A], b: B)(f: (B, A) => B): B = fa match
      case TableNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                              => b

    def foldRight[A, B](fa: SqlF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case TableNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                              => lb

  given DialectInfo[SqlF] = DialectInfo("SqlF", 3)

  given HasName[SqlF] with

    def name[A](fa: SqlF[A]): String = fa match
      case TableNodeF(n, _, _, _, _) => n
      case ViewNodeF(n, _, _, _)     => n
      case IndexNodeF(n, _, _, _)    => n

  given HasNested[SqlF] with

    def nested[A](fa: SqlF[A]): Vector[A] = fa match
      case TableNodeF(_, _, _, nested, _) => nested
      case _                              => Vector.empty

// ============================================================
// Lowering algebra + type aliases
// ============================================================

object SqlDialect:
  import SqlF._

  type SqlIR = SqlF :+: SemanticF

  val sqlQueryKey: Meta.Key[String]                = Meta.Key("sql.query")
  val sqlIndexTableKey: Meta.Key[String]           = Meta.Key("sql.indexTable")
  val sqlIndexColumnsKey: Meta.Key[Vector[String]] = Meta.Key("sql.indexColumns")

  /** Lowering algebra: converts each SqlF operation to a SemanticF subtree. */
  val sqlToSemantic: Algebra[SqlF, Fix[SemanticF]] = {
    case TableNodeF(name, columns, constraints, nested, meta) =>
      val annotations = constraints.map(c => Annotation(c))
      Fix[SemanticF](
        TypeDeclF(name, TypeKind.Product, fields = columns, nested = nested, annotations = annotations, meta = meta)
      )
    case ViewNodeF(name, query, columns, meta) =>
      Fix[SemanticF](TypeDeclF(name, TypeKind.Protocol, fields = columns, meta = meta.set(sqlQueryKey, query)))
    case IndexNodeF(name, tableName, columnNames, meta) =>
      Fix[SemanticF](
        ConstDeclF(
          name,
          TypeExpr.STR,
          Expr.Lit(s"$tableName(${columnNames.mkString(",")})", TypeExpr.STR),
          meta = meta.set(sqlIndexTableKey, tableName).set(sqlIndexColumnsKey, columnNames)
        )
      )
  }

  val eliminateSql: Fix[SqlIR] => Fix[SemanticF] =
    eliminate.dialect(sqlToSemantic)

// ============================================================
// Tests
// ============================================================

class SqlFDialectExample extends FunSuite {

  import SqlF._
  import SqlF.given
  import SqlDialect._

  val injS   = Inject[SqlF, SqlIR]
  val injSem = Inject[SemanticF, SqlIR]

  // ---- Step A: Functor/Traverse ----

  test("Functor map identity on TableNodeF") {
    val t: SqlF[Int] = TableNodeF("users", Vector.empty, nested = Vector(1, 2))
    assertEquals(Traverse[SqlF].map(t)(identity), t)
  }

  test("Traverse on TableNodeF sequences nested") {
    val t: SqlF[Int] = TableNodeF("users", Vector.empty, nested = Vector(1, 2, 3))
    val result       = Traverse[SqlF].traverse(t)(x => Option(x.toString))
    assertEquals(result, Some(TableNodeF("users", Vector.empty, nested = Vector("1", "2", "3"))))
  }

  test("foldLeft on ViewNodeF returns initial (data-only)") {
    assertEquals(Traverse[SqlF].foldLeft(ViewNodeF[Int]("v", "SELECT 1"), 0)(_ + _), 0)
  }

  test("HasName on all SqlF variants") {
    assertEquals(HasName[SqlF].name(TableNodeF("users", Vector.empty)), "users")
    assertEquals(HasName[SqlF].name(ViewNodeF("v", "SELECT 1")), "v")
    assertEquals(HasName[SqlF].name(IndexNodeF("idx", "t", Vector("col"))), "idx")
  }

  test("HasNested on TableNodeF returns nested") {
    assertEquals(HasNested[SqlF].nested(TableNodeF("t", Vector.empty, nested = Vector(1, 2))), Vector(1, 2))
  }

  test("HasNested on ViewNodeF returns empty") {
    assertEquals(HasNested[SqlF].nested(ViewNodeF[Int]("v", "q")), Vector.empty[Int])
  }

  test("DialectInfo[SqlF]") {
    assertEquals(DialectInfo[SqlF].dialectName, "SqlF")
    assertEquals(DialectInfo[SqlF].operationCount, 3)
  }

  // ---- Step B: Lowering algebra ----

  test("sqlToSemantic converts TableNodeF to TypeDeclF(Product)") {
    val cols   = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR))
    val result = sqlToSemantic(TableNodeF("users", cols, constraints = Vector("PRIMARY KEY(id)")))
    result.unfix match
      case TypeDeclF(name, kind, fields, _, _, _, _, _, annotations, _) =>
        assertEquals(name, "users")
        assertEquals(kind, TypeKind.Product)
        assertEquals(fields.size, 2)
        assertEquals(annotations.size, 1)
        assertEquals(annotations.head.name, "PRIMARY KEY(id)")
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("sqlToSemantic converts ViewNodeF to TypeDeclF(Protocol)") {
    val cols   = Vector(Field("email", TypeExpr.STR))
    val result = sqlToSemantic(ViewNodeF("active_users", "SELECT * FROM users WHERE active", cols))
    result.unfix match
      case TypeDeclF(name, kind, fields, _, _, _, _, _, _, meta) =>
        assertEquals(name, "active_users")
        assertEquals(kind, TypeKind.Protocol)
        assertEquals(fields.size, 1)
        assertEquals(meta.get(sqlQueryKey), Some("SELECT * FROM users WHERE active"))
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("sqlToSemantic converts IndexNodeF to ConstDeclF") {
    val result = sqlToSemantic(IndexNodeF("idx_email", "users", Vector("email")))
    result.unfix match
      case ConstDeclF(name, _, _, _, meta) =>
        assertEquals(name, "idx_email")
        assertEquals(meta.get(sqlIndexTableKey), Some("users"))
        assertEquals(meta.get(sqlIndexColumnsKey), Some(Vector("email")))
      case other => fail(s"expected ConstDeclF, got $other")
  }

  test("sqlToSemantic handles nested tables") {
    val inner = Fix[SemanticF](TypeDeclF("addresses", TypeKind.Product, fields = Vector(Field("street", TypeExpr.STR))))
    val result =
      sqlToSemantic(TableNodeF("users", columns = Vector(Field("id", TypeExpr.LONG)), nested = Vector(inner)))
    result.unfix match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) =>
        assertEquals(nested.size, 1)
        assertEquals(SemanticF.name(nested.head.unfix), "addresses")
      case other => fail(s"expected TypeDeclF, got $other")
  }

  // ---- Step C: E2E pipeline ----

  test("E2E: SqlTable -> SqlIR -> eliminate -> emit Java") {
    // Build SqlIR tree via Inject
    val usersTable = Fix[SqlIR](
      injS.inj(
        TableNodeF(
          "User",
          columns = Vector(
            Field("id", TypeExpr.LONG, mutability = Mutability.Mutable),
            Field("name", TypeExpr.STR, mutability = Mutability.Mutable),
            Field("email", TypeExpr.STR, mutability = Mutability.Mutable)
          ),
          constraints = Vector("PRIMARY KEY(id)")
        )
      )
    )

    val activeView = Fix[SqlIR](
      injS.inj(
        ViewNodeF(
          "ActiveUser",
          query = "SELECT * FROM users WHERE active = true",
          columns = Vector(
            Field("name", TypeExpr.STR),
            Field("email", TypeExpr.STR)
          )
        )
      )
    )

    // Eliminate SqlF -> SemanticF
    val cleanUser = eliminateSql(usersTable)
    val cleanView = eliminateSql(activeView)

    // Wrap in Module and emit
    val module = Module(
      "sql-demo",
      Vector(
        CompilationUnit("com.example.model", Vector(cleanUser, cleanView))
      )
    )

    val files = JavaEmitter[Id].apply(module)
    assertEquals(files.size, 2)

    val userSource = files.values.find(_.contains("class User")).get
    assert(userSource.contains("public class User"), "expected class User")
    assert(userSource.contains("public long id;"), "expected id field")
    assert(userSource.contains("public String name;"), "expected name field")

    val viewSource = files.values.find(_.contains("interface ActiveUser")).get
    assert(viewSource.contains("public interface ActiveUser"), "expected interface ActiveUser")
  }

  test("E2E: generic pass collectAllNames works on SqlIR") {
    val tree = Fix[SqlIR](
      injS.inj(
        TableNodeF(
          "root",
          columns = Vector.empty,
          nested = Vector(
            Fix[SqlIR](injS.inj(ViewNodeF("child_view", "SELECT 1"))),
            Fix[SqlIR](injSem.inj(TypeDeclF[Fix[SqlIR]]("ExistingSemantic", TypeKind.Product)))
          )
        )
      )
    )

    def collectAllNames[F[_]: Traverse: HasName]: Fix[F] => Vector[String] =
      scheme.cata[F, Vector[String]] { fa =>
        Vector(HasName[F].name(fa)) ++ Traverse[F].foldLeft(fa, Vector.empty[String])(_ ++ _)
      }

    val names = collectAllNames[SqlIR].apply(tree)
    assertEquals(names.toSet, Set("root", "child_view", "ExistingSemantic"))
  }

  test("E2E: eliminateSql on mixed SqlF + SemanticF tree") {
    val mixed = Fix[SqlIR](
      injS.inj(
        TableNodeF(
          "outer",
          columns = Vector(Field("x", TypeExpr.INT)),
          nested = Vector(
            Fix[SqlIR](injSem.inj(TypeDeclF[Fix[SqlIR]]("Inner", TypeKind.Product)))
          )
        )
      )
    )

    val clean = eliminateSql(mixed)
    clean.unfix match
      case TypeDeclF(name, _, fields, _, nested, _, _, _, _, _) =>
        assertEquals(name, "outer")
        assertEquals(fields.size, 1)
        assertEquals(nested.size, 1)
        assertEquals(SemanticF.name(nested.head.unfix), "Inner")
      case other => fail(s"expected TypeDeclF, got $other")
  }
}
