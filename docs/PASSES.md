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
