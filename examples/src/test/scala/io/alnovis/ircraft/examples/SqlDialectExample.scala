package io.alnovis.ircraft.examples

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.Traversal.*

/**
  * End-to-end example: SQL Schema dialect.
  *
  * Demonstrates the full Generic Dialect API workflow:
  *   1. Define dialect (tables, columns, indexes)
  *   2. Build IR from a schema
  *   3. Transform with passes (add audit columns, add primary key index)
  *   4. Emit SQL DDL
  */
class SqlDialectExample extends munit.FunSuite:

  // ── 1. Define the dialect ────────────────────────────────────────────

  val Sql = GenericDialect("sql", "SQL schema definition"):
    leaf("column", "name" -> StringField, "type"    -> StringField, "nullable"   -> BoolField, "default" -> StringField)
    leaf("index", "name"  -> StringField, "columns" -> StringListField, "unique" -> BoolField)
    container("table", "name" -> StringField)("columns", "indexes")

  // ── 2. Extractors for pattern matching ───────────────────────────────

  val Table  = Sql.extractor("table")
  val Column = Sql.extractor("column")
  val Index  = Sql.extractor("index")

  // ── 3. Helpers: build a table ────────────────────────────────────────

  private def column(name: String, tpe: String, nullable: Boolean = false, default: String = ""): GenericOp =
    Sql("column", "name" -> name, "type" -> tpe, "nullable" -> nullable, "default" -> default)

  private def index(name: String, columns: List[String], unique: Boolean = false): GenericOp =
    Sql("index", "name" -> name, "columns" -> columns, "unique" -> unique)

  private def table(name: String, cols: Vector[GenericOp], idxs: Vector[GenericOp] = Vector.empty): GenericOp =
    Sql("table", "name" -> name, "columns" -> cols, "indexes" -> idxs)

  // ── 4. Passes ────────────────────────────────────────────────────────

  /** Adds created_at/updated_at columns to every table. */
  val addAuditColumns = Sql.transformPass("add-audit-columns"):
    case t if t.is("table") =>
      val existing = t.children("columns")
      val hasCreatedAt = existing.exists:
        case Column(c) => c.stringField("name").contains("created_at")
        case _         => false
      if hasCreatedAt then t
      else
        val audit = Vector(
          column("created_at", "TIMESTAMP", default = "NOW()"),
          column("updated_at", "TIMESTAMP", default = "NOW()")
        )
        t.withRegion("columns", existing ++ audit)

  /** Adds a primary key index on 'id' column if the table has one. */
  val addPrimaryKeyIndex = Sql.transformPass("add-pk-index"):
    case t if t.is("table") =>
      val hasId = t
        .children("columns")
        .exists:
          case Column(c) => c.stringField("name").contains("id")
          case _         => false
      if !hasId then t
      else
        val existingIndexes = t.children("indexes")
        val hasPk = existingIndexes.exists:
          case Index(i) => i.stringField("name").exists(_.endsWith("_pkey"))
          case _        => false
        if hasPk then t
        else
          val tableName = t.stringField("name").getOrElse("unknown")
          val pk        = index(s"${tableName}_pkey", List("id"), unique = true)
          t.withRegion("indexes", existingIndexes :+ pk)

  // ── 5. Simple DDL emitter ────────────────────────────────────────────

  private def emitDdl(module: IrModule): String =
    val sb = StringBuilder()
    module.topLevel.foreach:
      case Table(t) =>
        val name = t.stringField("name").getOrElse("unknown")
        sb.append(s"CREATE TABLE $name (\n")
        val cols = t
          .children("columns")
          .collect:
            case Column(c) =>
              val colName  = c.stringField("name").getOrElse("?")
              val colType  = c.stringField("type").getOrElse("TEXT")
              val nullable = if c.boolField("nullable").getOrElse(false) then "" else " NOT NULL"
              val default  = c.stringField("default").filter(_.nonEmpty).map(d => s" DEFAULT $d").getOrElse("")
              s"  $colName $colType$nullable$default"
        sb.append(cols.mkString(",\n"))
        sb.append("\n);\n\n")

        t.children("indexes")
          .foreach:
            case Index(i) =>
              val idxName = i.stringField("name").getOrElse("idx")
              val unique  = if i.boolField("unique").getOrElse(false) then "UNIQUE " else ""
              val idxCols = i.stringListField("columns").getOrElse(Nil).mkString(", ")
              sb.append(s"CREATE ${unique}INDEX $idxName ON $name ($idxCols);\n")
            case _ => ()
        sb.append("\n")
      case _ => ()
    sb.result().trim

  // ── Tests ────────────────────────────────────────────────────────────

  test("define and emit a simple table"):
    val users = table(
      "users",
      Vector(
        column("id", "BIGSERIAL"),
        column("email", "VARCHAR(255)"),
        column("name", "VARCHAR(100)", nullable = true)
      )
    )
    val ddl = emitDdl(IrModule("schema", Vector(users)))

    assert(ddl.contains("CREATE TABLE users"))
    assert(ddl.contains("id BIGSERIAL NOT NULL"))
    assert(ddl.contains("email VARCHAR(255) NOT NULL"))
    assert(ddl.contains("name VARCHAR(100)"))
    assert(!ddl.contains("name VARCHAR(100) NOT NULL"))

  test("audit pass adds created_at and updated_at"):
    val users  = table("users", Vector(column("id", "BIGSERIAL")))
    val result = addAuditColumns.run(IrModule("schema", Vector(users)), PassContext())
    assert(result.isSuccess)

    val cols = result.module.topLevel.flatMap(_.collectAll:
      case Column(c) => c.stringField("name").get)
    assertEquals(cols.toSet, Set("id", "created_at", "updated_at"))

  test("audit pass is idempotent"):
    val users = table(
      "users",
      Vector(
        column("id", "BIGSERIAL"),
        column("created_at", "TIMESTAMP")
      )
    )
    val result = addAuditColumns.run(IrModule("schema", Vector(users)), PassContext())
    val cols = result.module.topLevel.flatMap(_.collectAll:
      case Column(c) => c.stringField("name").get)
    assertEquals(cols.count(_ == "created_at"), 1)

  test("pk index pass adds primary key"):
    val users  = table("users", Vector(column("id", "BIGSERIAL"), column("name", "TEXT")))
    val result = addPrimaryKeyIndex.run(IrModule("schema", Vector(users)), PassContext())

    val indexes = result.module.topLevel.flatMap(_.collectAll:
      case Index(i) => i.stringField("name").get)
    assertEquals(indexes, Vector("users_pkey"))

  test("full pipeline: schema -> audit -> pk -> DDL"):
    val users = table(
      "users",
      Vector(
        column("id", "BIGSERIAL"),
        column("email", "VARCHAR(255)"),
        column("name", "VARCHAR(100)", nullable = true)
      )
    )
    val orders = table(
      "orders",
      Vector(
        column("id", "BIGSERIAL"),
        column("user_id", "BIGINT"),
        column("total", "DECIMAL(10,2)")
      )
    )

    val pipeline = Pipeline("sql-schema", addAuditColumns, addPrimaryKeyIndex)
    val result   = pipeline.run(IrModule("schema", Vector(users, orders)), PassContext())
    assert(result.isSuccess)

    val ddl = emitDdl(result.module)

    // Both tables have audit columns
    assert(ddl.contains("created_at TIMESTAMP NOT NULL DEFAULT NOW()"))
    assert(ddl.contains("updated_at TIMESTAMP NOT NULL DEFAULT NOW()"))

    // Both tables have PK indexes
    assert(ddl.contains("CREATE UNIQUE INDEX users_pkey ON users (id)"))
    assert(ddl.contains("CREATE UNIQUE INDEX orders_pkey ON orders (id)"))

    // Original columns preserved
    assert(ddl.contains("email VARCHAR(255) NOT NULL"))
    assert(ddl.contains("user_id BIGINT NOT NULL"))

  test("extractor + withField in transform"):
    val users = table(
      "users",
      Vector(
        column("id", "BIGSERIAL"),
        column("email", "TEXT")
      )
    )

    val fixTypes = Sql.transformPass("fix-text-types"):
      case c if c.is("column") && c.stringField("type").contains("TEXT") =>
        c.withField("type", "VARCHAR(255)")

    val result = fixTypes.run(IrModule("schema", Vector(users)), PassContext())
    val types = result.module.topLevel.flatMap(_.collectAll:
      case Column(c) => c.stringField("type").get)
    assertEquals(types, Vector("BIGSERIAL", "VARCHAR(255)"))
