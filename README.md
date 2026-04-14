# ircraft

Pure functional framework for building code generators with open-world extensible IR. Scala 3 + Cats.

> Design background: [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation)

## What It Does

```
Source Schema (Proto, OpenAPI, SQL, your DSL)
  |  Define as functor F[+A]
  v
Dialect IR: Fix[F :+: SemanticF]
  |  eliminate.dialect -- type-safe coproduct shrinking
  v
Semantic IR: Fix[SemanticF]
  |  Passes (Kleisli, composable via andThen)
  v
Enriched IR
  |  Emitter (Java, Scala 3, Scala 2, your language)
  v
Generated Source Code
```

ircraft provides the algebra (Fix, Coproduct, Inject), recursion schemes (cata, ana, hylo), semantic IR, trait mixins for generic passes, constraint system, and multi-language emitters. You provide the source dialect and business logic.

**Adding a new dialect requires zero changes to ircraft core.**

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
    CompilationUnit("com.example.model", Vector(Decl.typeDecl(
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
```

## Core Concepts

| Concept | Type | Description |
|---------|------|-------------|
| **Fix[F]** | `case class` | Fixpoint -- recursive IR tree from functor F |
| **Coproduct[F, G, A]** | `:+:` | Disjoint union of dialect functors |
| **Inject[F, G]** | typeclass | Type-safe injection into coproducts |
| **SemanticF[+A]** | functor | Core language-agnostic IR (TypeDeclF, EnumDeclF, FuncDeclF, AliasDeclF, ConstDeclF) |
| **Module[D]** | `case class` | IR tree root, parameterized over declaration type |
| **Pass** | `Kleisli[F, Module, Module]` | Composable transformation. `Pipeline.of(a, b, c)` |
| **scheme.cata/ana/hylo** | functions | Stack-safe recursion schemes via Eval |
| **eliminate.dialect** | function | Type-safe coproduct shrinking (ProtoF :+: SemanticF -> SemanticF) |
| **Outcome** | `IorT[F, NonEmptyChain[Diagnostic], A]` | Success / Warnings+Success / Error |
| **LanguageSyntax** | `trait` | Parameterizes emitter for any target language |

`F[_]` is tagless final: `Id` for tests, `IO` for production.

## Extensible Dialects

New dialect = enum F[+A] + Traverse + DialectInfo + trait mixins + lowering algebra. Zero core changes.

```scala
// Define dialect functor
enum SqlF[+A]:
  case TableNodeF(name: String, columns: Vector[Field], nested: Vector[A])
  case ViewNodeF(name: String, query: String)

// Provide Traverse instance (3-rule template)
// Provide trait mixin instances (HasName, HasNested, ...)
// Write lowering algebra: SqlF -> SemanticF
// Done -- use with cata, ana, eliminate, generic passes
```

See [Dialects Guide](docs/DIALECTS.md) and `examples/` for full SQL dialect implementation.

### Trait Mixins

Generic passes work across ANY dialect via structural typeclasses:

| Trait | Extracts |
|-------|----------|
| `HasName[F]` | declaration name |
| `HasFields[F]` | fields vector |
| `HasMethods[F]` | functions vector |
| `HasNested[F]` | nested declarations |
| `HasMeta[F]` | typed metadata |
| `HasVisibility[F]` | visibility modifier |

Coproduct instances auto-derived. Write `collectAllNames` once -- works on ProtoF, SqlF, any future dialect.

### Constraint System

```scala
// Define constraints
val resolved: Fix[SemanticF] !> MustBeResolved = ???

// Verify via cata
ConstraintVerifier.verifyFieldTypes[F, SemanticF, MustBeResolved](tree)
```

Custom constraints with zero core changes. `Constrained[A, C]` wrapper + `!>` infix type (Scala 3).

## Semantic IR

Language-agnostic. Describes **what**, not **how**.

```
TypeDeclF(Product)   -> Java class, Scala class, Rust struct, Go struct
TypeDeclF(Protocol)  -> Java interface, Scala trait, Rust trait
TypeDeclF(Abstract)  -> abstract class
EnumDeclF            -> Java enum, Scala 3 enum, Scala 2 sealed trait
AliasDeclF           -> Scala type alias (Java: skipped)
Func                 -> method / def / fn
Field                -> field / val / property
Stmt.Match           -> Java if-chain, Scala match, Rust match
```

Types: `Primitive` (Bool, Int32, Str, ...), `ListOf`, `MapOf`, `Optional`, `SetOf`, `TupleOf`, `FuncType`, `Union`, `Intersection`.

## Multi-Language Emitters

```scala
JavaEmitter[IO]          // .java, public class, interface, ;, new, <T>
ScalaEmitter.scala3[IO]  // .scala, trait, enum, def, val, type X = Y, match
ScalaEmitter.scala2[IO]  // .scala, sealed trait, new, if (cond)
```

Adding a new language = implement `LanguageSyntax` + `TypeMapping`. All traversal is reused from `BaseEmitter`.

See [Emitters Guide](docs/EMITTERS.md) for details.

## Error Handling

Unified via `IorT` (cats). Smart constructors work on all Scala versions:

```scala
Outcome.ok(module)              // Ior.Right -- clean success
Outcome.warn("deprecated", m)   // Ior.Both  -- success with warning
Outcome.fail("unresolved type") // Ior.Left  -- error, pipeline stops
```

## Modules

| Module | Description | Dependencies |
|--------|-------------|-------------|
| `ircraft-core` | Fix, Coproduct, Inject, scheme.*, SemanticF, Pass, Pipeline, Merge, trait mixins, constraints | cats-core |
| `ircraft-emit` | CodeNode, Renderer, LanguageSyntax, BaseEmitter | ircraft-core |
| `ircraft-io` | CodeWriter, IncrementalWriter (atomic writes) | cats-effect |
| `ircraft-dialect-proto` | ProtoF functor + ProtoLowering + trait mixin instances | ircraft-core |
| `ircraft-emitter-java` | JavaEmitter + JavaSyntax + JavaTypeMapping | ircraft-emit |
| `ircraft-emitter-scala` | ScalaEmitter + ScalaSyntax + ScalaTypeMapping | ircraft-emit |
| `ircraft-java-api` | Java facade: Result, IrNode, IrModule, IrPass, IrVisitor, IrEmitter | all above |

## Documentation

- **[User Guide](docs/GUIDE.md)** -- start here: step-by-step from schema to generated code
- **[Scala 3 API Guide](docs/SCALA3_API.md)** -- full API with FP-MLIR, dialects, recursion schemes
- **[Scala 2 API Guide](docs/SCALA2_API.md)** -- same, adapted for Scala 2.12/2.13
- **[Java API Guide](docs/JAVA_API.md)** -- using ircraft from Java (Maven/Gradle)
- [Passes Guide](docs/PASSES.md) -- how to write passes, compose pipelines, handle errors
- [Dialects Guide](docs/DIALECTS.md) -- how to create source dialects, extensible IR, trait mixins
- [Emitters Guide](docs/EMITTERS.md) -- how emitters work, LanguageSyntax, create your own
- [Architecture](docs/ARCHITECTURE.md) -- FP-MLIR architecture, Fix/Coproduct, recursion schemes

## Cross-Compilation

ircraft is cross-compiled for **Scala 2.12**, **2.13**, and **3.x**:

```scala
// build.sbt
libraryDependencies += "io.alnovis" %% "ircraft-core" % "2.0.0-alpha.2"
// works with Scala 2.12.20, 2.13.16, 3.6.4
```

sbt plugins (Scala 2.12) can depend on ircraft directly.

## Tech Stack

- **Scala 2.12 / 2.13 / 3.6.4** (cross-compiled)
- **Cats 2.12.0** (cats-core in core, cats-effect 3.5.7 in io)
- **MUnit 1.1.0**
- **sbt 1.10.11**

## License

Apache License 2.0
