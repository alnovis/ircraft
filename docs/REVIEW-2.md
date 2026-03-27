# Architecture Review #2 (2026-03-24)

## Quick Fixes

| # | Issue | Status |
|---|-------|--------|
| 9 | ParameterizedType hash uses `.sum` (commutative) → use `ContentHash.ofList` | DONE (2026-03-27) |
| 3 | Kotlin BitwiseNot emits `inv()expr` → should be `expr.inv()` | DONE (2026-03-27) |
| 4 | Scala empty trait/class body emits `()` → should omit or use `end` | DONE (2026-03-27) |
| 19 | Pipeline fail-fast returns failed pass module → should return last good | DONE (2026-03-27) |

## Medium Fixes

| # | Issue | Status |
|---|-------|--------|
| 6 | collectImports incomplete — missing constructor params, enum fields, method params in Kotlin/Scala | DONE (2026-03-27) |
| 2 | ContentHash skips typeParams, javadoc, annotations, defaultValue in many ops | DONE (2026-03-27) |
| 7 | Absent-version fields have no stub implementation in impl class → uncompilable code | DONE (2026-03-27) |
| 5 | Scala emitter: ReturnStmt omits `return` keyword → breaks early return semantics | DONE (2026-03-27) |

## Accepted / Deferred

| # | Issue | Decision |
|---|-------|----------|
| 1 | ClassTag in regionOps | Accepted: smart constructors guarantee types. ClassTag adds complexity without real benefit. |
| 8 | structurallyEquals 32-bit only | Deferred: <10K nodes, collision probability negligible. Add deep compare when needed. |
| 10 | GenericDialect asInstanceOf[List[String]] | Known: documented trade-off for Any-based DSL. |
| 20 | Traversal no trampolining | Deferred: proto schemas ~5-10 depth. Stack overflow unrealistic. |
| 18 | Kotlin dead code for `open` modifier | DONE (2026-03-27): removed unreachable branch. |
| 21 | Nested messages lose oneofs in lowering | DONE (2026-03-27): lowerToInterfaceOp now handles msg.oneofs. |
| 11 | No Java emitter test suite | TODO (P2): only covered via EndToEndSuite. |
| 12 | No Kotlin/Scala pipelines | DONE (2026-03-27): ProtoToCodePipeline(config, emitter) in dialectProto. ProtoToJavaPipeline delegates. |
| 13 | No type mapping tests | TODO (P2): BaseLanguageTypeMapping + dialect mappings. |
| 14 | CI: no coverage reporting | Deferred: add scoverage after 1.0.0. |
| 15 | Release workflow no publish | TODO (P2): add sbt publish to release.yml. |
| 16 | Version hardcoded 0.1.0-SNAPSHOT | TODO (P2): add sbt-dynver or sbt-ci-release. |
| 17 | Emitter duplication ~70% | DONE (2026-03-27): BaseEmitter in dialectSemantic. Shared: emit, emitSource, collectImports, emitExpr, emitBinOp, emitBlock, typeOpName. |
| 22 | SemanticDialect.verify is no-op | TODO (P2): add structural validation. |
