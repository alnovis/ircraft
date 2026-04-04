# ircraft v2: Implementation Plan

## Status

Branch: `main` (merged from `v2-pure-fp`)

| Phase | Description | Status | Tests |
|-------|-------------|--------|-------|
| 1 | Core ADT + Pass/Pipeline (Kleisli) | done | 29 |
| 2 | Emitter infra + CodeNode tree + Java emitter | done | 20 |
| 3 | Merge with MergeStrategy[F] + Outcome (IorT) | done | (in core) |
| 4 | Proto dialect ADT + Lowering | done | 13 |
| 5 | IO module + SQL dialect example | done | 4 |
| 6 | LanguageSyntax refactoring + Scala emitter | done | 21 |
| 7 | Pattern matching IR (Stmt.Match + Pattern ADT) | done | (in core) |
| 8 | Doc system (structured, Meta-based) | done | (in emitter) |
| 9 | proto-wrapper CLI (separate repo) | done | 30 |
| 10 | Kotlin emitter | planned | -- |
| 11 | OpenAPI dialect | planned | -- |
| 12 | GraphQL dialect | planned | -- |
| 13 | Cross-compilation (2.12/2.13/3.x) | planned | -- |

**Total: 87 ircraft tests + 30 CLI tests = 117 tests (all green)**

## Architecture decisions

1. **Cats + cats-effect** -- cats-core in core, cats-effect in io module
2. **Tagless final `F[_]`** on the entire path
3. **Outcome (IorT)** -- unified error handling: Right/Both/Left for success/warnings/errors
4. **Pass = Kleisli**, Pipeline = andThen composition
5. **Dialect = user's ADT** -- no mandatory base traits
6. **Semantic IR is language-agnostic** -- TypeDecl/Func/Field, not Interface/Class/Constructor
7. **Two-phase emit** -- IR -> CodeNode tree (via LanguageSyntax) -> text (via Renderer)
8. **LanguageSyntax trait** -- ~30 hooks parameterize BaseEmitter for any language
9. **Multi-language** -- Java + Scala 3 + Scala 2 emitters, adding new language = implement 2 traits
10. **Pattern matching IR** -- Stmt.Match + Pattern ADT, language-agnostic
11. **Doc as Meta** -- structured Doc ADT, rendered by LanguageSyntax
12. **File IO in separate module** with atomic writes via Resource
13. **CLI as universal interface** -- proto-wrapper CLI replaces build-tool-specific plugins

## What's next

### Planned features
- Kotlin emitter (KotlinSyntax + KotlinTypeMapping)
- OpenAPI source dialect
- GraphQL source dialect
- Cross-compilation to Scala 2.12/2.13 for sbt plugin ecosystem
- GraalVM native-image for CLI

### proto-wrapper CLI (separate repo: sbt-proto-wrapper)
- DescriptorConverter (Scala port from Java)
- 13 enrichment passes as pure functions
- ProtoMergeStrategy with 14-level conflict detection
- `--language scala3|scala2|java`
- sbt wrapper plugin (planned)
- Maven wrapper plugin v3 (planned, replaces v2.x)
