# ircraft v2: Implementation Plan

## Status

Branch: `v2-pure-fp`

| Phase | Description | Status | Tests |
|-------|-------------|--------|-------|
| 1 | Core ADT + Pass/Pipeline (Kleisli + WriterT) | done | 27 |
| 2 | Emitter infra + CodeNode tree + Java emitter | done | 20 |
| 3 | Merge with MergeStrategy[F] | done | (in core) |
| 4 | Proto dialect ADT + Lowering | done | 13 |
| 5 | IO module + SQL dialect example | done | 5 |
| 6 | Plugin integration (Java bridge) | planned | -- |
| 7 | Kotlin emitter | planned | -- |
| 8 | Scala emitter | planned | -- |
| 9 | OpenAPI dialect | planned | -- |
| 10 | GraphQL dialect | planned | -- |

**Total: 31 files, 2722 lines, 65 tests (all green)**

## Architecture decisions

1. **Cats + cats-effect** -- cats-core in core, cats-effect in io module
2. **Tagless final `F[_]`** on the entire path
3. **WriterT** for diagnostic accumulation (`Pipe[F, A] = WriterT[F, Chain[Diagnostic], A]`)
4. **Pass = Kleisli** (`Pass[F] = Kleisli[Pipe[F, *], Module, Module]`), composable via `andThen`
5. **Dialect = user's ADT** -- no mandatory base traits, no registration
6. **Semantic IR is language-agnostic** -- `TypeDecl`/`Func`/`Field`, not `Interface`/`Class`/`Constructor`
7. **Two-phase emit** -- IR -> `Pipe[F, CodeNode]` (effectful tree construction) -> `Renderer.render` (pure text)
8. **CodeNode tree** for structured code generation -- `TypeBlock`, `Func`, `IfElse`, not string concat
9. **File IO in separate module** (`ircraft-io`) with cats-effect
10. **MergeStrategy[F]** -- user provides conflict resolution, ircraft provides generic merge

## Module structure

```
ircraft/
  ircraft-core/          cats-core only. ADT, types, Pipe/Pass/Pipeline/Lowering, Merge
  ircraft-emit/          CodeNode, Renderer, TypeMapping, ImportCollector, BaseEmitter
  ircraft-io/            cats-effect. CodeWriter, IncrementalWriter
  dialects/
    proto/               Proto ADT + ProtoLowering
  emitters/
    java/                JavaEmitter, JavaTypeMapping
  examples/              SQL dialect end-to-end example
```

## Phase 6: Plugin integration (next)

- Create `ircraft-java-bridge/` with thin Java API
- Expose `Module`, `Pass`, `Pipeline`, `Lowering` to Java callers via static methods
- Bridge runs `unsafeRunSync()` at the boundary
- Switch `proto-wrapper-plugin` from v1 ircraft to v2
- Rewrite 13 passes as `Pass[IO]` functions
- Target: 0 broken files in golden tests

## Phase 7-10: Additional emitters and dialects

- Kotlin emitter: `KotlinEmitter` + `KotlinTypeMapping` (val/var, data class, ?, etc.)
- Scala emitter: `ScalaEmitter` + `ScalaTypeMapping` (case class, Option, trait, etc.)
- OpenAPI dialect: OpenAPI 3.0 ADT + lowering (schemas, paths, operations)
- GraphQL dialect: GraphQL type system ADT + lowering
