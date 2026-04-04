# Passes Guide

## What is a Pass

A pass transforms an IR Module. It's a pure function wrapped in Kleisli:

```scala
type Pass[F[_]] = Kleisli[F, Module, Module]
```

`F[_]` is your effect type: `Id` for pure tests, `IO` for production, `Outcome[Id, *]` for error handling.

## Creating Passes

### Pure pass (no effects)

```scala
val addTimestamps = Pass.pure[Id]("add-timestamps") { module =>
  // transform module, return new module
  module.copy(units = module.units.map(transformUnit))
}
```

### Effectful pass

```scala
val validate = Pass[IO]("validate") { module =>
  IO.println(s"Validating ${module.units.size} units").as(module)
}
```

### Pass with Outcome (warnings/errors)

```scala
type OF[A] = Outcome[Id, A]

// Warn but continue
val warnEmpty = Pass[OF]("warn-empty") { module =>
  val empty = module.units.flatMap(_.declarations).count(_.name.isEmpty)
  if empty > 0 then Outcome.warn(s"$empty empty names", module)
  else Outcome.ok(module)
}

// Fail and stop
val requireFields = Pass[OF]("require-fields") { module =>
  val bad = findTypesWithoutFields(module)
  if bad.nonEmpty then Outcome.fail(s"No fields: ${bad.mkString(", ")}")
  else Outcome.ok(module)
}
```

### Pass trace

Every pass appends its name to `Pass.traceKey` in module meta. Inspect after pipeline run:

```scala
val trace = result.meta.get(Pass.traceKey)
// Some(Chain("add-timestamps", "add-getters", "validate"))
```

## Common Pass Patterns

### Add fields to all types

```scala
val addId = Pass.pure[Id]("add-id") { module =>
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      td.copy(fields = Field("id", TypeExpr.LONG) +: td.fields)
    case other => other
  }
}

def transformDecls(module: Module)(f: Decl => Decl): Module =
  module.copy(units = module.units.map(u =>
    u.copy(declarations = u.declarations.map(f))))
```

### Add methods from fields

```scala
val addGetters = Pass.pure[Id]("add-getters") { module =>
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      val getters = td.fields.map { f =>
        Func(s"get${f.name.capitalize}", returnType = f.fieldType,
          body = Some(Body.of(Stmt.Return(Some(Expr.Access(Expr.This, f.name))))))
      }
      td.copy(functions = td.functions ++ getters)
    case other => other
  }
}
```

### Filter declarations

```scala
val excludeInternal = Pass.pure[Id]("exclude-internal") { module =>
  module.copy(units = module.units.map { unit =>
    unit.copy(declarations = unit.declarations.filter(d =>
      !d.name.startsWith("_")))
  })
}
```

### Add annotations

```scala
val addNotNull = Pass.pure[Id]("add-notnull") { module =>
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      td.copy(fields = td.fields.map { f =>
        f.fieldType match
          case TypeExpr.Optional(_) => f  // nullable, skip
          case _ => f.copy(annotations = f.annotations :+ Annotation("NotNull"))
      })
    case other => other
  }
}
```

### Type resolution

```scala
val resolveTypes = Pass.pure[Id]("resolve-types") { module =>
  val knownNames = module.units.flatMap(_.declarations).map(_.name).toSet
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      td.copy(fields = td.fields.map(f =>
        f.copy(fieldType = resolve(f.fieldType, knownNames))))
    case other => other
  }
}

def resolve(t: TypeExpr, known: Set[String]): TypeExpr = t match
  case TypeExpr.Unresolved(fqn) =>
    val simple = fqn.split('.').last
    if known.contains(simple) then TypeExpr.Local(simple) else t
  case TypeExpr.ListOf(e)    => TypeExpr.ListOf(resolve(e, known))
  case TypeExpr.Optional(i)  => TypeExpr.Optional(resolve(i, known))
  case _                     => t
```

### Attach documentation

```scala
val addDocs = Pass.pure[Id]("add-docs") { module =>
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      val doc = Doc(summary = s"Generated from ${td.name} schema.")
      td.copy(meta = td.meta.set(Doc.key, doc))
    case other => other
  }
}
```

## Pipeline Composition

### Sequential

```scala
val pipeline = Pipeline.of(
  resolveTypes,
  addGetters,
  addNotNull,
  addDocs,
)

val result = Pipeline.run(pipeline, module)
```

### Conditional

```scala
case class Config(generateBuilders: Boolean, addDocs: Boolean)

def buildPipeline(config: Config): Pass[Id] =
  val passes = Vector(
    Some(resolveTypes),
    Some(addGetters),
    Option.when(config.generateBuilders)(addBuilders),
    Option.when(config.addDocs)(addDocs),
  ).flatten
  passes.reduceLeft[Pass[Id]](_ andThen _)
```

### With Outcome

```scala
type OF[A] = Outcome[Id, A]

val pipeline = Pipeline.of[OF](
  validateNames,       // may warn
  resolveTypes,        // pure
  requireFields,       // may fail
  addGetters,          // pure (skipped if requireFields fails)
)

Pipeline.run(pipeline, module).value match
  case Ior.Right(m)    => emit(m)
  case Ior.Both(ws, m) => ws.toList.foreach(w => log(w.message)); emit(m)
  case Ior.Left(es)    => es.toList.foreach(e => error(e.message))
```

## Built-in Passes

```scala
import io.alnovis.ircraft.core.Passes

// Fails if any Unresolved types remain
Passes.validateResolved[Id]  // Pass[Outcome[Id, *]]
```

Run before emission to catch unresolved type references.

## Meta: Pass Communication

Passes communicate through `Meta` on Module, Decl, Func, Field:

```scala
// Define a typed key
val mergeKey = Meta.Key[Vector[String]]("merge.presentIn")

// Pass 1: set meta
val annotate = Pass.pure[Id]("annotate") { module =>
  transformDecls(module) {
    case td: Decl.TypeDecl =>
      td.copy(meta = td.meta.set(mergeKey, Vector("v1", "v2")))
    case other => other
  }
}

// Pass 2: read meta
val check = Pass.pure[Id]("check") { module =>
  module.units.flatMap(_.declarations).foreach { d =>
    d.meta.get(mergeKey).foreach(versions => println(s"${d.name}: $versions"))
  }
  module
}
```

Keys are identity-based (vault-style) -- type-safe, no erasure issues.

## Testing Passes

Passes are pure functions -- test without any framework infrastructure:

```scala
class MyPassSuite extends munit.FunSuite:
  val input = Module("test", Vector(CompilationUnit("com.example", Vector(
    Decl.TypeDecl("User", TypeKind.Product, fields = Vector(Field("name", TypeExpr.STR)))
  ))))

  test("addGetters creates getter for each field"):
    val result = Pipeline.run(addGetters, input)
    val td = result.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    assertEquals(td.functions.size, 1)
    assertEquals(td.functions.head.name, "getName")

  test("pipeline composes correctly"):
    val pipeline = Pipeline.of(addTimestamps, addGetters)
    val result = Pipeline.run(pipeline, input)
    val td = result.units.head.declarations.head.asInstanceOf[Decl.TypeDecl]
    assert(td.fields.exists(_.name == "createdAt"))
    assert(td.functions.exists(_.name == "getCreatedAt"))
```

## Merge and Pluggable Strategies

### What Merge Does

Merge combines N versions of the same schema into one unified Module. It detects conflicts (same name, different types) and delegates resolution to your strategy.

```scala
import io.alnovis.ircraft.core.merge.*

Merge.merge(
  NonEmptyVector.of(("v1", moduleV1), ("v2", moduleV2)),
  myStrategy
)
// returns Outcome[F, Module]
```

This is not limited to proto version merging. Any scenario where multiple sources must be reconciled into one IR works:

- API versions (v1 + v2 + v3)
- Schema migration (before + after)
- Multi-source aggregation (DB schema + API spec -> unified model)
- Feature flags (base + feature branch models)
- Multi-tenant (common + tenant-specific schemas)

### MergeStrategy Trait

```scala
trait MergeStrategy[F[_]]:
  def onConflict(conflict: Conflict): Outcome[F, Resolution]
```

ircraft calls `onConflict` for every detected conflict. You decide what to do.

### Conflict

```scala
case class Conflict(
  declName: String,               // type name (e.g., "User")
  memberName: String,             // field/function name (e.g., "getStatus")
  kind: ConflictKind,             // FieldType | FuncReturnType | Missing
  versions: NonEmptyVector[(String, TypeExpr)],  // version -> type per source
  meta: Meta,
)
```

### Resolution Options

```scala
sealed trait Resolution

Resolution.UseType(typeExpr)     // pick one type for the merged result
Resolution.DualAccessor(types)   // keep both types as separate accessors
Resolution.Custom(decls)         // provide entirely custom declarations
Resolution.Skip                  // drop the conflicting member
```

### Example: Pick Wider Type

```scala
val widenStrategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict): Outcome[Id, Resolution] =
    conflict.kind match
      case ConflictKind.FuncReturnType =>
        // pick the wider type (Int32 -> Int64)
        val widest = conflict.versions.toVector.map(_._2).maxBy(typeWidth)
        Outcome.warn(
          s"Widened ${conflict.declName}.${conflict.memberName}",
          Resolution.UseType(widest)
        )
      case _ =>
        Outcome.ok(Resolution.UseType(conflict.versions.head._2))

def typeWidth(t: TypeExpr): Int = t match
  case TypeExpr.Primitive.Int32  => 32
  case TypeExpr.Primitive.Int64  => 64
  case TypeExpr.Primitive.Float32 => 32
  case TypeExpr.Primitive.Float64 => 64
  case _                          => 0
```

### Example: Fail on Any Conflict

```scala
val strictStrategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict): Outcome[Id, Resolution] =
    Outcome.fail(
      s"Breaking change: ${conflict.declName}.${conflict.memberName} " +
      s"changed type across versions: ${conflict.versions.toVector.map(_._2).mkString(" vs ")}"
    )
```

### Example: Schema Migration (not proto)

Merge works with any Module, not just proto. Here's a database schema migration scenario:

```scala
// Two versions of DB schema
val before = Module("v1", Vector(CompilationUnit("com.example.model", Vector(
  Decl.TypeDecl("User", TypeKind.Product, fields = Vector(
    Field("name", TypeExpr.STR),
    Field("email", TypeExpr.STR),
  ))
))))

val after = Module("v2", Vector(CompilationUnit("com.example.model", Vector(
  Decl.TypeDecl("User", TypeKind.Product, fields = Vector(
    Field("name", TypeExpr.STR),
    Field("email", TypeExpr.Optional(TypeExpr.STR)),  // became nullable
    Field("phone", TypeExpr.STR),                      // new field
  ))
))))

// Strategy: detect breaking changes
val migrationStrategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict): Outcome[Id, Resolution] =
    conflict.kind match
      case ConflictKind.FieldType =>
        // nullable change is safe; type change is breaking
        val types = conflict.versions.toVector.map(_._2)
        if types.exists(_.isInstanceOf[TypeExpr.Optional]) then
          Outcome.warn(s"${conflict.memberName} became nullable", Resolution.UseType(types.last))
        else
          Outcome.fail(s"Breaking: ${conflict.memberName} changed type")
      case ConflictKind.Missing =>
        Outcome.ok(Resolution.UseType(conflict.versions.head._2))
      case _ =>
        Outcome.ok(Resolution.Skip)

val result = Merge.merge(NonEmptyVector.of(("before", before), ("after", after)), migrationStrategy)
// Ior.Both(warnings about nullable, merged Module with all fields)
```

### Example: Multi-Source Aggregation

Combine models from different sources into one:

```scala
// From REST API spec
val apiModule = Module("api", Vector(CompilationUnit("com.example", Vector(
  Decl.TypeDecl("User", TypeKind.Protocol, functions = Vector(
    Func("getName", returnType = TypeExpr.STR),
    Func("getEmail", returnType = TypeExpr.STR),
  ))
))))

// From database schema
val dbModule = Module("db", Vector(CompilationUnit("com.example", Vector(
  Decl.TypeDecl("User", TypeKind.Product, fields = Vector(
    Field("name", TypeExpr.STR),
    Field("email", TypeExpr.STR),
    Field("passwordHash", TypeExpr.STR),  // DB-only, not in API
  ))
))))

// Strategy: union all members, warn on type mismatches
val unionStrategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict): Outcome[Id, Resolution] =
    Outcome.warn(
      s"${conflict.memberName}: merged from multiple sources",
      Resolution.UseType(conflict.versions.last._2)
    )

val unified = Merge.merge(NonEmptyVector.of(("api", apiModule), ("db", dbModule)), unionStrategy)
// Merged Module has both API methods and DB fields including passwordHash
```

### Using Merge in Pipeline

Merge returns `Outcome[F, Module]` -- plug it into your pipeline:

```scala
type OF[A] = Outcome[Id, A]

val merged: OF[Module] = Merge.merge(versions, strategy)

val result: OF[Map[Path, String]] = merged.flatMap { module =>
  val enriched: OF[Module] = Pipeline.of[OF](resolveTypes, addGetters).run(module)
  enriched.map(m => ScalaEmitter.scala3[Id].apply(m))
}

result.value match
  case Ior.Right(files) => writeFiles(files)
  case Ior.Both(warnings, files) => logWarnings(warnings); writeFiles(files)
  case Ior.Left(errors) => reportErrors(errors)
```

## Pluggable Strategies: Beyond Merge

MergeStrategy is one example of a broader pattern: **a pass parameterized by a user-provided strategy**. ircraft does the traversal; the user provides the decision logic.

This pattern works for any scenario where the framework handles structure and the user handles policy.

### The Pattern

```scala
// 1. Define a strategy trait
trait EnrichmentStrategy[F[_]]:
  def enrich(decl: Decl): Outcome[F, Decl]

// 2. Write a generic pass parameterized by the strategy
def enrichTypes[F[_]: Monad](strategy: EnrichmentStrategy[F]): Pass[[A] =>> Outcome[F, A]] =
  Pass[[A] =>> Outcome[F, A]]("enrich-types") { module =>
    module.units.flatMap(_.declarations).traverse(strategy.enrich).map { enriched =>
      module.copy(units = Vector(CompilationUnit(
        module.units.head.namespace, enriched)))
    }
  }

// 3. User provides their implementation
val addDefaultId = new EnrichmentStrategy[Id]:
  def enrich(decl: Decl): Outcome[Id, Decl] = decl match
    case td: Decl.TypeDecl if !td.fields.exists(_.name == "id") =>
      Outcome.warn(
        s"${td.name} has no id field, adding one",
        td.copy(fields = Field("id", TypeExpr.LONG) +: td.fields)
      )
    case other => Outcome.ok(other)

// 4. Use in pipeline
val pipeline = Pipeline.of(enrichTypes(addDefaultId), addGetters)
```

### Example: Validation Strategy

Different projects have different rules for what's valid:

```scala
trait ValidationPolicy[F[_]]:
  def onEmptyType(td: Decl.TypeDecl): Outcome[F, Decl.TypeDecl]
  def onUnresolvedType(fqn: String, context: String): Outcome[F, Unit]
  def onDeprecatedField(field: Field, typeName: String): Outcome[F, Field]

def validate[F[_]: Monad](policy: ValidationPolicy[F]): Pass[[A] =>> Outcome[F, A]] =
  Pass[[A] =>> Outcome[F, A]]("validate") { module =>
    module.units.flatMap(_.declarations).traverse {
      case td: Decl.TypeDecl =>
        for
          checked <- if td.fields.isEmpty then policy.onEmptyType(td) else Outcome.ok(td)
          fields  <- checked.fields.traverse { f =>
            val isDeprecated = f.annotations.exists(_.name.contains("Deprecated"))
            if isDeprecated then policy.onDeprecatedField(f, td.name)
            else Outcome.ok(f)
          }
        yield checked.copy(fields = fields)
      case other => Outcome.ok(other)
    }.map(decls => module.copy(units = Vector(CompilationUnit(module.units.head.namespace, decls))))
  }

// Strict: no empty types, no deprecated fields
val strict = new ValidationPolicy[Id]:
  def onEmptyType(td: Decl.TypeDecl) = Outcome.fail(s"${td.name} has no fields")
  def onUnresolvedType(fqn: String, ctx: String) = Outcome.fail(s"Unresolved: $fqn in $ctx")
  def onDeprecatedField(f: Field, tn: String) = Outcome.fail(s"Deprecated field $tn.${f.name}")

// Lenient: warn but continue
val lenient = new ValidationPolicy[Id]:
  def onEmptyType(td: Decl.TypeDecl) = Outcome.warn(s"${td.name} is empty", td)
  def onUnresolvedType(fqn: String, ctx: String) = Outcome.warn(s"Unresolved: $fqn", ())
  def onDeprecatedField(f: Field, tn: String) = Outcome.warn(s"Deprecated: $tn.${f.name}", f)
```

### Example: Code Generation Strategy

Different products generate different code from the same IR:

```scala
trait CodeGenStrategy[F[_]]:
  def onProtocol(td: Decl.TypeDecl): Outcome[F, Vector[Decl]]

def expandProtocols[F[_]: Monad](strategy: CodeGenStrategy[F]): Pass[[A] =>> Outcome[F, A]] =
  Pass[[A] =>> Outcome[F, A]]("expand-protocols") { module =>
    module.units.flatMap(_.declarations).flatTraverse {
      case td: Decl.TypeDecl if td.kind == TypeKind.Protocol =>
        strategy.onProtocol(td).map(_.toVector)
      case other =>
        Outcome.ok(Vector(other))
    }.map(decls => module.copy(units = Vector(CompilationUnit(module.units.head.namespace, decls))))
  }

// proto-wrapper style: interface + abstract class + version impls
val protoWrapperGen = new CodeGenStrategy[Id]:
  def onProtocol(td: Decl.TypeDecl) = Outcome.ok(Vector(
    td,                                          // keep interface
    buildAbstractClass(td),                       // add abstract base
    buildImpl(td, "v1"),                          // add version impl
  ))

// simple style: just the interface, nothing else
val simpleGen = new CodeGenStrategy[Id]:
  def onProtocol(td: Decl.TypeDecl) = Outcome.ok(Vector(td))

// builder style: interface + builder
val builderGen = new CodeGenStrategy[Id]:
  def onProtocol(td: Decl.TypeDecl) = Outcome.ok(Vector(
    td,
    buildBuilderInterface(td),
    buildAbstractBuilder(td),
  ))
```

### When to Use This Pattern

| Scenario | Strategy trait | Pass does |
|----------|---------------|-----------|
| Version merge | `MergeStrategy` | Detect conflicts, call strategy for resolution |
| Validation | `ValidationPolicy` | Walk IR, call strategy for each violation |
| Code expansion | `CodeGenStrategy` | Walk declarations, call strategy for generation decisions |
| Type resolution | `TypeResolver` | Find unresolved types, call strategy for mapping |
| Naming | `NamingPolicy` | Walk names, call strategy for convention |
| Filtering | `FilterPolicy` | Walk declarations, call strategy for include/exclude |

The key insight: **ircraft handles the traversal; the user handles the decisions**. This inverts the control -- the user doesn't need to write boilerplate tree walking, only the domain-specific logic.
