# ircraft

Pure functional framework for building code generators. Scala 3 + Cats.

> Design background: [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation)

## What It Does

```
Source Schema (Proto, OpenAPI, SQL, your DSL)
  |  Lowering (your function)
  v
Language-Agnostic IR (Module, TypeDecl, Func, Field)
  |  Passes (composable transformations)
  v
Enriched IR
  |  Emitter (Java, Scala 3, Scala 2, your language)
  v
Generated Source Code
```

ircraft provides the IR, passes, pipeline composition, and emitters. You provide the source dialect and business logic.

## Quick Start

```scala
import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.emitters.scala.ScalaEmitter

// 1. Define your source (just case classes)
case class SqlTable(name: String, columns: Vector[(String, String)])

// 2. Define lowering
val lowering: Lowering[Id, Vector[SqlTable]] = Lowering.pure { tables =>
  Module("sql", tables.map { t =>
    CompilationUnit("com.example.model", Vector(Decl.TypeDecl(
      name = t.name,
      kind = TypeKind.Product,
      fields = t.columns.map((name, typ) => Field(name, sqlType(typ)))
    )))
  })
}

// 3. Define passes
val addGetters = Pass.pure[Id]("add-getters") { module => /* ... */ module }

// 4. Compose pipeline
val pipeline = Pipeline.of(addGetters)

// 5. Run
val module = lowering(Vector(SqlTable("User", Vector("id" -> "BIGINT", "name" -> "VARCHAR"))))
val enriched = Pipeline.run(pipeline, module)
val files = ScalaEmitter.scala3[Id].apply(enriched)
// files: Map[Path, String] with User.scala containing:
//   class User {
//     val id: Long
//     val name: String
//   }
```

## Core Concepts

| Concept | Type | Description |
|---------|------|-------------|
| **Module** | `case class` | IR tree root. Contains CompilationUnits with Decls |
| **Pass** | `Kleisli[F, Module, Module]` | Composable transformation. `Pipeline.of(a, b, c)` |
| **Lowering** | `Kleisli[F, Source, Module]` | Your source -> IR conversion |
| **Emitter** | `Kleisli[F, Module, Map[Path, String]]` | IR -> source files |
| **Outcome** | `IorT[F, NonEmptyChain[Diagnostic], A]` | Success / Warnings+Success / Error |
| **LanguageSyntax** | `trait` | Parameterizes emitter for any target language |

`F[_]` is tagless final: `Id` for tests, `IO` for production.

## Semantic IR

Language-agnostic. Describes **what**, not **how**.

```
TypeDecl(Product)    -> Java class, Scala class, Rust struct, Go struct
TypeDecl(Protocol)   -> Java interface, Scala trait, Rust trait
TypeDecl(Abstract)   -> abstract class
EnumDecl             -> Java enum, Scala 3 enum, Scala 2 sealed trait
Func                 -> method / def / fn
Field                -> field / val / property
Stmt.Match           -> Java if-chain, Scala match, Rust match
```

Types: `Primitive` (Bool, Int32, Str, ...), `ListOf`, `MapOf`, `Optional`, `SetOf`, `TupleOf`, `FuncType`, `Union`, `Intersection`.

## Multi-Language Emitters

```scala
// Java
JavaEmitter[IO]          // .java, public class, interface, ;, new, <T>

// Scala 3
ScalaEmitter.scala3[IO]  // .scala, trait, enum, def, val, Option[T], match, =>

// Scala 2
ScalaEmitter.scala2[IO]  // .scala, sealed trait, new, if (cond)
```

Adding a new language = implement `LanguageSyntax` + `TypeMapping`. All traversal is reused from `BaseEmitter`.

See [Emitters Guide](docs/EMITTERS.md) for details.

## Error Handling: Outcome

Unified via `IorT` (cats):

```scala
Outcome.ok(module)              // Ior.Right -- clean success
Outcome.warn("deprecated", m)   // Ior.Both  -- success with warning
Outcome.fail("unresolved type") // Ior.Left  -- error, pipeline stops
```

## Modules

| Module | Description | Dependencies |
|--------|-------------|-------------|
| `ircraft-core` | IR ADTs, Pass, Pipeline, Outcome, Merge | cats-core |
| `ircraft-emit` | CodeNode, Renderer, LanguageSyntax, BaseEmitter | ircraft-core |
| `ircraft-io` | CodeWriter, IncrementalWriter (atomic writes) | cats-effect |
| `ircraft-dialect-proto` | Proto source ADT + ProtoLowering | ircraft-core |
| `ircraft-emitter-java` | JavaEmitter + JavaSyntax + JavaTypeMapping | ircraft-emit |
| `ircraft-emitter-scala` | ScalaEmitter + ScalaSyntax + ScalaTypeMapping | ircraft-emit |

## Documentation

- **[User Guide](docs/GUIDE.md)** -- start here: step-by-step from schema to generated code
- [Passes Guide](docs/PASSES.md) -- how to write passes, compose pipelines, handle errors
- [Dialects Guide](docs/DIALECTS.md) -- how to create source dialects, Lowering, Meta, Doc, Merge
- [Emitters Guide](docs/EMITTERS.md) -- how emitters work, LanguageSyntax, create your own
- [Architecture](docs/ARCHITECTURE.md) -- internal architecture and design decisions

## Tech Stack

- **Scala 3.6.4**
- **Cats 2.12.0** (cats-core in core, cats-effect 3.5.7 in io)
- **MUnit 1.1.0**
- **sbt 1.10.11**

## License

Apache License 2.0
