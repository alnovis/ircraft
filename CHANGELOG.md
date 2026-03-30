# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Semantic merged into core** -- `ircraft-dialect-semantic` module removed, all Semantic ops now in `ircraft-core` under `io.alnovis.ircraft.core.semantic`. Semantic is the platform, not an optional module.
- **Proto lowering moved to dialect** -- `pipelines/proto-to-semantic/` eliminated, lowering now lives in `dialects/proto/lowering/`. No separate pipeline modules needed.
- **Simplified dependencies** -- all dialects and java-api depend only on `core`. No more `dialectSemantic` dependency.

### Added
- **New Proto Dialect** -- simple single-schema representation (ProtoFileOp, MessageOp, FieldOp, EnumOp, OneofOp). No versioning. TypeRef encodes all type info.
- **Proto DSL** -- `ProtoSchema.file("pkg", Proto3) { ... }` builder
- **Proto-to-Semantic lowering** -- MessageOp->InterfaceOp, EnumOp->EnumClassOp, proto syntax-aware has-method generation
- **ProtoVerifierPass** -- structural validation for proto IR

### Removed
- **Old Proto Dialect** -- deleted versioned proto dialect (presentInVersions, ConflictType, etc.)
- **Old Proto Pipelines** -- deleted `pipelines/proto-to-java/`, `proto-to-kotlin/`, `proto-to-scala/`
- **SchemaDiffApi** -- removed from `ircraft-java-api`
- **Obsolete docs** -- PASS_SEPARATION_PLAN.md, IMPLEMENTATION_PLAN.md, PHASE8_PLAN.md

### Added
- **Scala Code Dialect** — DirectScalaEmitter with Scala 3 syntax (trait, enum, case extends, Option[T], Array[Byte], square bracket generics, companion object, ScalaDoc)
- **Kotlin Code Dialect** — DirectKotlinEmitter with Kotlin syntax (interface, data class, ByteArray, nullable T?, companion object, KDoc)
- **IR Text Printer** — IrPrinter for human-readable IR visualization (MLIR-style format)
- **Generic Dialect API** — GenericDialect + GenericOp for zero-boilerplate dialect creation (~6 lines vs ~241 lines)
- **BaseLanguageTypeMapping** — shared type mapping logic for JVM languages, reducing duplication across Java/Kotlin/Scala
- **Expression/Statement traversal** -- ExprTraversal + BodyTraversal for method body walk/collect/transform

### Changed
- **Dialect isolation** -- Semantic dialect is pure (zero imports from other dialects)
- **width -> estimatedSize** -- renamed across all Operations for clarity
- **ContentHashable consistency** -- standardized summon[] usage, AttributeMap.contentHash direct access
- **Operation.regionOps** -- warns on stderr if region name not found on non-leaf operation

### Fixed
- **ContentHash for bodies** — MethodOp.body, ConstructorOp.body, EnumConstantOp.arguments now included in hash
- **asInstanceOf removal** — replaced with type ascription in lowering
- **Exhaustive matching** — ProtoVerifierPass and DirectJavaEmitter warn on unknown operation types

## [0.1.0] - 2025-11-05

### Added
- **ircraft-core** — GreenNode, Operation, Module, Region, TypeRef, Dialect, Pass, Pipeline, Traversal, ContentHash, Attribute, EmitterUtils
- **ircraft-dialect-proto** — SchemaOp, MessageOp, FieldOp, EnumOp, OneofOp, ConflictEnumOp, ProtoVerifierPass, ProtoSchema DSL
- **ircraft-dialect-semantic** — ClassOp, InterfaceOp, MethodOp, EnumClassOp, FieldDeclOp, ConstructorOp, Expression, Statement, Block, SemanticDialect
- **ircraft-dialect-java** — DirectJavaEmitter, JavaTypeMapping
- **ircraft-pipeline-proto-to-java** — ProtoToJavaPipeline end-to-end
- Proto → Semantic lowering (ProtoToSemanticLowering)
- Integration with proto-wrapper-plugin (IrcraftBridge, IrcraftGenerator)
