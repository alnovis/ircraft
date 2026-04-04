package io.alnovis.ircraft.examples

import cats.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emitters.java.JavaEmitter

/**
  * End-to-end example: SQL Schema -> IR -> Pipeline -> Java code.
  *
  * Demonstrates the full ircraft v2 workflow:
  *   1. Define source dialect as ADT (SqlSchema)
  *   2. Define Lowering (SqlSchema -> Module)
  *   3. Define custom passes (add audit fields, add @NotNull)
  *   4. Compose pipeline
  *   5. Emit Java source
  */
class SqlDialectExample extends munit.FunSuite:

  type F[A] = Id[A]

  // -- 1. Source dialect: just an ADT --------------------------------------

  case class SqlTable(name: String, columns: Vector[SqlColumn])
  case class SqlColumn(name: String, sqlType: String, nullable: Boolean, default: Option[String] = None)

  // -- 2. Lowering: SqlTable -> Module ------------------------------------

  val sqlLowering: Lowering[F, Vector[SqlTable]] = Lowering.pure { tables =>
    val units = tables.map { table =>
      val fields = table.columns.map { col =>
        Field(
          name = col.name,
          fieldType = sqlTypeToIr(col.sqlType),
          mutability = Mutability.Mutable,
          defaultValue = col.default.map(d => Expr.Lit(d, TypeExpr.STR)),
          annotations =
            if !col.nullable then Vector(Annotation("NotNull"))
            else Vector.empty
        )
      }
      CompilationUnit(
        namespace = "com.example.model",
        declarations = Vector(
          Decl.TypeDecl(
            name = table.name,
            kind = TypeKind.Product,
            fields = fields
          )
        )
      )
    }
    Module("sql-schema", units)
  }

  private def sqlTypeToIr(sqlType: String): TypeExpr = sqlType.toUpperCase match
    case "BIGSERIAL" | "BIGINT" | "INT8"                                              => TypeExpr.LONG
    case "SERIAL" | "INTEGER" | "INT" | "INT4"                                        => TypeExpr.INT
    case "BOOLEAN" | "BOOL"                                                           => TypeExpr.BOOL
    case "DOUBLE PRECISION" | "FLOAT8"                                                => TypeExpr.DOUBLE
    case "REAL" | "FLOAT4"                                                            => TypeExpr.FLOAT
    case s if s.startsWith("VARCHAR") || s.startsWith("TEXT") || s.startsWith("CHAR") => TypeExpr.STR
    case s if s.startsWith("DECIMAL") || s.startsWith("NUMERIC")                      => TypeExpr.DOUBLE
    case "TIMESTAMP" | "TIMESTAMPTZ" => TypeExpr.Named("java.time.Instant")
    case _                           => TypeExpr.STR

  // -- 3. Custom passes ---------------------------------------------------

  val addAuditFields: Pass[F] = Pass.pure[F]("add-audit-fields") { module =>
    module.copy(units = module.units.map { unit =>
      unit.copy(declarations = unit.declarations.map {
        case td: Decl.TypeDecl =>
          val hasCreatedAt = td.fields.exists(_.name == "created_at")
          if hasCreatedAt then td
          else
            td.copy(fields =
              td.fields ++ Vector(
                Field("created_at", TypeExpr.Named("java.time.Instant"), mutability = Mutability.Immutable),
                Field("updated_at", TypeExpr.Named("java.time.Instant"), mutability = Mutability.Immutable)
              )
            )
        case other => other
      })
    })
  }

  val addGetters: Pass[F] = Pass.pure[F]("add-getters") { module =>
    module.copy(units = module.units.map { unit =>
      unit.copy(declarations = unit.declarations.map {
        case td: Decl.TypeDecl =>
          val getters = td.fields.map { f =>
            val getterName = s"get${f.name.split("_").map(s => s.head.toUpper +: s.tail).mkString}"
            Func(
              name = getterName,
              returnType = f.fieldType,
              body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, f.name)))))
            )
          }
          td.copy(functions = td.functions ++ getters)
        case other => other
      })
    })
  }

  // -- 4. Pipeline --------------------------------------------------------

  val pipeline: Pass[F] = Pipeline.of(addAuditFields, addGetters)

  // -- 5. Emit Java -------------------------------------------------------

  val javaEmitter = JavaEmitter[F]

  // -- Tests --------------------------------------------------------------

  test("full pipeline: SQL tables -> Java source"):
    val tables = Vector(
      SqlTable(
        "User",
        Vector(
          SqlColumn("id", "BIGSERIAL", nullable = false),
          SqlColumn("name", "VARCHAR(255)", nullable = false),
          SqlColumn("email", "VARCHAR(255)", nullable = true)
        )
      ),
      SqlTable(
        "Order",
        Vector(
          SqlColumn("id", "BIGSERIAL", nullable = false),
          SqlColumn("user_id", "BIGINT", nullable = false),
          SqlColumn("total", "DECIMAL(10,2)", nullable = false)
        )
      )
    )

    // lowering
    val module = sqlLowering(tables)
    assertEquals(module.units.size, 2)

    // pipeline
    val enriched = Pipeline.run(pipeline, module)

    // verify audit fields added
    val userDecl = enriched.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    assert(userDecl.fields.exists(_.name == "created_at"))
    assert(userDecl.fields.exists(_.name == "updated_at"))

    // verify getters added
    val getterNames = userDecl.functions.map(_.name)
    assert(getterNames.contains("getId"))
    assert(getterNames.contains("getName"))
    assert(getterNames.contains("getCreatedAt"))

    // emit Java
    val files = javaEmitter(enriched)
    assertEquals(files.size, 2)

    val userSource = files.values.find(_.contains("class User")).get
    assert(userSource.contains("package com.example.model;"))
    assert(userSource.contains("public class User"))
    assert(userSource.contains("public long id;"))
    assert(userSource.contains("@NotNull"))
    assert(userSource.contains("public long getId()"))
    assert(userSource.contains("return this.id;"))
    assert(userSource.contains("Instant created_at;"))

    val orderSource = files.values.find(_.contains("class Order")).get
    assert(orderSource.contains("public class Order"))
    assert(orderSource.contains("double total;"))

  test("pipeline is composable -- add extra pass"):
    val tables = Vector(
      SqlTable(
        "Item",
        Vector(
          SqlColumn("id", "SERIAL", nullable = false),
          SqlColumn("name", "TEXT", nullable = false)
        )
      )
    )

    val addToString: Pass[F] = Pass.pure[F]("add-toString") { module =>
      module.copy(units = module.units.map { unit =>
        unit.copy(declarations = unit.declarations.map {
          case td: Decl.TypeDecl =>
            val toString = Func(
              name = "toString",
              returnType = TypeExpr.STR,
              modifiers = Set(FuncModifier.Override),
              body = Some(
                Body.of(
                  Stmt.Return(
                    Some(
                      Expr.Call(Some(Expr.Access(Expr.This, "name")), "toString")
                    )
                  )
                )
              )
            )
            td.copy(functions = td.functions :+ toString)
          case other => other
        })
      })
    }

    val fullPipeline = Pipeline.of(pipeline, addToString)
    val module       = sqlLowering(tables)
    val enriched     = Pipeline.run(fullPipeline, module)
    val files        = javaEmitter(enriched)

    val source = files.values.head
    assert(source.contains("getId"))
    assert(source.contains("getCreatedAt"))
    assert(source.contains("toString"))
