# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Scala Code Dialect** — DirectScalaEmitter with Scala 3 syntax (trait, enum, case extends, Option[T], Array[Byte], square bracket generics, companion object, ScalaDoc)
- **Kotlin Code Dialect** — DirectKotlinEmitter with Kotlin syntax (interface, data class, ByteArray, nullable T?, companion object, KDoc)
- **IR Text Printer** — IrPrinter for human-readable IR visualization (MLIR-style format)
- **Generic Dialect API** — GenericDialect + GenericOp for zero-boilerplate dialect creation (~6 lines vs ~241 lines)
- **BaseLanguageTypeMapping** — shared type mapping logic for JVM languages, reducing duplication across Java/Kotlin/Scala
- **Feature parity passes** — 13 nanopass passes for proto-wrapper compatibility:
  - ConflictResolutionPass (INT_ENUM, STRING_BYTES, PRIMITIVE_MESSAGE)
  - HasMethodsPass (hasXxx, supportsXxx)
  - ProtoWrapperPass (base interface generation)
  - CommonMethodsPass (equals, hashCode, toString, toBytes)
  - VersionContextPass (factory interface + per-version impls)
  - ProtocolVersionsPass (version constants)
  - VersionConversionPass (asVersion, getFieldsInaccessibleInVersion)
  - BuilderPass (conditional builder pattern)
  - WktConversionPass (Timestamp→Instant, Duration, Struct→Map)
  - ValidationAnnotationsPass (@NotNull for non-optional fields)
  - SchemaMetadataPass (SchemaInfo per version)
- **Nested message support** — recursive lowering for nested abstract + impl classes
- **Expression/Statement traversal** — ExprTraversal + BodyTraversal for method body walk/collect/transform
- **Proto metadata attributes** — ProtoAttributes carried through Semantic IR for pass isolation
- **Error recovery in lowering** — Try-wrapped lowering helpers, partial output on failure

### Changed
- **Dialect isolation** — Semantic dialect is now pure (zero imports from other dialects). Lowering moved to proto dialect. Pipelines are separate modules.
- **ProtoToJavaPipeline** — now includes all 13 passes (was 2)
- **width → estimatedSize** — renamed across all Operations for clarity
- **ContentHashable consistency** — standardized summon[] usage, AttributeMap.contentHash direct access
- **Operation.regionOps** — warns on stderr if region name not found on non-leaf operation

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
