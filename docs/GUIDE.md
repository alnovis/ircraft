# ircraft User Guide

A step-by-step guide to building a code generator with ircraft.

## Table of Contents

1. [Overview](#1-overview)
2. [Define Your Source](#2-define-your-source)
3. [Write a Lowering](#3-write-a-lowering)
4. [Write Passes](#4-write-passes)
5. [Compose a Pipeline](#5-compose-a-pipeline)
6. [Choose an Emitter](#6-choose-an-emitter)
7. [Handle Results](#7-handle-results)
8. [Full Example](#8-full-example)
9. [Next Steps](#9-next-steps)

---

## 1. Overview

ircraft turns **your schema** into **generated code** through a pipeline of transformations:

```
Your Schema -> [Lowering] -> IR -> [Pass] -> [Pass] -> ... -> [Emitter] -> Source Files
```

You provide:
- **Source dialect** -- your schema as Scala ADT (case classes)
- **Lowering** -- function that converts your schema to ircraft IR
- **Passes** -- functions that enrich/transform the IR
- **Configuration** -- which emitter, which passes enabled

ircraft provides:
- **IR** -- language-agnostic representation (Module, TypeDecl, Func, Field, ...)
- **Pipeline** -- composable pass execution with error handling
- **Emitters** -- Java, Scala 3, Scala 2 (or write your own)
- **Outcome** -- unified error/warning handling

---

## 2. Define Your Source

Your source dialect is just Scala case classes. No traits to extend, no registration.

```scala
// Your domain model -- any shape you want
case class ApiEndpoint(
  path: String,
  method: String,
  requestType: String,
  responseType: String,
)

case class ApiSchema(
  name: String,
  endpoints: Vector[ApiEndpoint],
  types: Vector[ApiType],
)

case class ApiType(
  name: String,
  fields: Vector[(String, String)],  // (name, type)
)
```

That's it. No ircraft dependencies in your domain types.

> **Details:** [Dialects Guide](DIALECTS.md)

---

## 3. Write a Lowering

A Lowering converts your source into ircraft's IR. It's a function: `Source => F[Module]`.

```scala
import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*

val apiLowering: Lowering[Id, ApiSchema] = Lowering.pure { schema =>
  val units = schema.types.map { apiType =>
    CompilationUnit(
      namespace = "com.example.api",
      declarations = Vector(Decl.TypeDecl(
        name = apiType.name,
        kind = TypeKind.Product,
        fields = apiType.fields.map { (name, typ) =>
          Field(name, mapType(typ))
        }
      ))
    )
  }
  Module(schema.name, units)
}

def mapType(t: String): TypeExpr = t match
  case "string" => TypeExpr.STR
  case "int"    => TypeExpr.INT
  case "long"   => TypeExpr.LONG
  case "bool"   => TypeExpr.BOOL
  case other    => TypeExpr.Named(other)
```

### Key IR types

| IR Type | What it represents |
|---------|-------------------|
| `Module` | Root container with compilation units |
| `CompilationUnit` | One namespace (package) with declarations |
| `Decl.TypeDecl` | Class, trait, struct, interface |
| `Decl.EnumDecl` | Enumeration |
| `Func` | Method/function with optional body |
| `Field` | Property/field |
| `TypeExpr` | Type reference (primitives, collections, generics) |
| `Expr` / `Stmt` / `Body` | Code expressions and statements |

> **Details:** [Dialects Guide](DIALECTS.md)

---

## 4. Write Passes

A Pass is a function `Module => F[Module]`. It transforms the IR -- adds types, modifies methods, resolves references, validates.

### Simple pass (pure)

```scala
val addTimestamps = Pass.pure[Id]("add-timestamps") { module =>
  module.copy(units = module.units.map { unit =>
    unit.copy(declarations = unit.declarations.map {
      case td: Decl.TypeDecl =>
        td.copy(fields = td.fields ++ Vector(
          Field("createdAt", TypeExpr.Named("java.time.Instant")),
          Field("updatedAt", TypeExpr.Named("java.time.Instant")),
        ))
      case other => other
    })
  })
}
```

### Pass with warnings (Outcome)

```scala
type OF[A] = Outcome[Id, A]

val validateNames = Pass[OF]("validate-names") { module =>
  val emptyNames = module.units.flatMap(_.declarations).filter(_.name.isEmpty)
  if emptyNames.nonEmpty then
    Outcome.warn(s"${emptyNames.size} declarations have empty names", module)
  else
    Outcome.ok(module)
}
```

### Pass with errors (stops pipeline)

```scala
val requireFields = Pass[OF]("require-fields") { module =>
  val empty = module.units.flatMap(_.declarations).collect {
    case td: Decl.TypeDecl if td.fields.isEmpty => td.name
  }
  if empty.nonEmpty then
    Outcome.fail(s"Types without fields: ${empty.mkString(", ")}")
  else
    Outcome.ok(module)
}
```

### Built-in passes

```scala
// Validates no Unresolved types remain (run before emission)
Passes.validateResolved[Id]  // returns Pass[Outcome[Id, *]]
```

> **Details:** [Passes Guide](PASSES.md)

---

## 5. Compose a Pipeline

Passes compose left-to-right via `Pipeline.of`:

```scala
val pipeline = Pipeline.of(
  addTimestamps,
  addGetters,
  validateNames,
)
```

### Conditional passes

```scala
val pipeline = Pipeline.build(Vector(
  (addTimestamps, config.addAuditFields),
  (addGetters, true),
  (addBuilders, config.generateBuilders),
))
```

### Run pipeline

```scala
// With Id (pure, for tests)
val result: Module = Pipeline.run(pipeline, module)

// With Outcome (with error handling)
val result: Outcome[Id, Module] = Pipeline.run(outcomePipeline, module)
result.value match
  case Ior.Right(module)           => // clean success
  case Ior.Both(warnings, module)  => // success with warnings
  case Ior.Left(errors)            => // failure
```

> **Details:** [Passes Guide](PASSES.md)

---

## 6. Choose an Emitter

An emitter converts IR to source files. ircraft provides Java and Scala emitters.

```scala
import io.alnovis.ircraft.emitters.java.JavaEmitter
import io.alnovis.ircraft.emitters.scala.ScalaEmitter

// Java output
val javaFiles = JavaEmitter[Id].apply(module)
// Map(Path("com/example/api/User.java") -> "public class User { ... }")

// Scala 3 output
val scalaFiles = ScalaEmitter.scala3[Id].apply(module)
// Map(Path("com/example/api/User.scala") -> "class User { val name: String ... }")

// Scala 2 output
val scala2Files = ScalaEmitter.scala2[Id].apply(module)
```

### Same IR, different output

The same `Module` can be emitted to any language. Passes are language-agnostic -- they transform the IR without knowing the target.

> **Details:** [Emitters Guide](EMITTERS.md)

---

## 7. Handle Results

### Outcome: three states

ircraft uses `Outcome` (IorT from cats) for unified error handling:

```scala
import cats.data.Ior
import io.alnovis.ircraft.core.*

// Three possible states:
Outcome.ok(result)              // Ior.Right  -- success
Outcome.warn("note", result)    // Ior.Both   -- success with warning
Outcome.fail("broken")          // Ior.Left   -- error, stops pipeline
```

### Pattern matching on result

```scala
Pipeline.run(pipeline, module).value match
  case Ior.Right(module) =>
    val files = ScalaEmitter.scala3[Id].apply(module)
    writeFiles(outputDir, files)

  case Ior.Both(warnings, module) =>
    warnings.toList.foreach(w => println(s"WARNING: ${w.message}"))
    val files = ScalaEmitter.scala3[Id].apply(module)
    writeFiles(outputDir, files)

  case Ior.Left(errors) =>
    errors.toList.foreach(e => println(s"ERROR: ${e.message}"))
    sys.exit(1)
```

### Warnings accumulate through pipeline

If pass 1 warns and pass 2 warns, both warnings are collected in `Ior.Both`. If any pass fails (`Ior.Left`), pipeline stops immediately.

---

## 8. Full Example

Complete code generator: SQL schema -> Scala 3 code.

```scala
import cats.*
import cats.data.Ior
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emitters.scala.ScalaEmitter

// -- Source dialect --
case class SqlTable(name: String, columns: Vector[SqlColumn])
case class SqlColumn(name: String, sqlType: String, nullable: Boolean)

// -- Lowering --
val sqlLowering: Lowering[Id, Vector[SqlTable]] = Lowering.pure { tables =>
  Module("sql-schema", tables.map { table =>
    CompilationUnit("com.example.model", Vector(Decl.TypeDecl(
      name = table.name,
      kind = TypeKind.Product,
      fields = table.columns.map { col =>
        val baseType = col.sqlType.toUpperCase match
          case "BIGINT" | "BIGSERIAL" => TypeExpr.LONG
          case "INTEGER" | "SERIAL"   => TypeExpr.INT
          case "BOOLEAN"              => TypeExpr.BOOL
          case "DOUBLE PRECISION"     => TypeExpr.DOUBLE
          case _                      => TypeExpr.STR
        val fieldType = if col.nullable then TypeExpr.Optional(baseType) else baseType
        Field(col.name, fieldType)
      }
    )))
  })
}

// -- Passes --
val addAuditFields = Pass.pure[Id]("audit-fields") { module =>
  module.copy(units = module.units.map { unit =>
    unit.copy(declarations = unit.declarations.map {
      case td: Decl.TypeDecl => td.copy(fields = td.fields ++ Vector(
        Field("createdAt", TypeExpr.Named("java.time.Instant")),
        Field("updatedAt", TypeExpr.Named("java.time.Instant")),
      ))
      case other => other
    })
  })
}

val addGetters = Pass.pure[Id]("getters") { module =>
  module.copy(units = module.units.map { unit =>
    unit.copy(declarations = unit.declarations.map {
      case td: Decl.TypeDecl =>
        val getters = td.fields.map { f =>
          Func(s"get${f.name.capitalize}", returnType = f.fieldType,
            body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, f.name))))))
        }
        td.copy(functions = td.functions ++ getters)
      case other => other
    })
  })
}

// -- Pipeline --
val pipeline = Pipeline.of(addAuditFields, addGetters)

// -- Run --
val tables = Vector(
  SqlTable("User", Vector(
    SqlColumn("id", "BIGSERIAL", nullable = false),
    SqlColumn("name", "VARCHAR", nullable = false),
    SqlColumn("email", "VARCHAR", nullable = true),
  )),
)

val module = sqlLowering(tables)
val enriched = Pipeline.run(pipeline, module)
val files = ScalaEmitter.scala3[Id].apply(enriched)

files.foreach { (path, source) =>
  println(s"--- $path ---")
  println(source)
}
```

Output:

```scala
package com.example.model

import java.time.Instant

class User {
    val id: Long
    val name: String
    val email: Option[String]
    val createdAt: Instant
    val updatedAt: Instant

    def getId: Long = this.id
    def getName: String = this.name
    def getEmail: Option[String] = this.email
    def getCreatedAt: Instant = this.createdAt
    def getUpdatedAt: Instant = this.updatedAt
}
```

---

## 9. Next Steps

- [Passes Guide](PASSES.md) -- pass patterns, Outcome, conditional passes, validation
- [Emitters Guide](EMITTERS.md) -- how emitters work, LanguageSyntax, create your own language
- [Dialects Guide](DIALECTS.md) -- source dialect design, Lowering patterns, Meta, Doc
- [Architecture](ARCHITECTURE.md) -- internal architecture, design decisions
