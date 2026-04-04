# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Cross-compilation: Scala 2.12.20, 2.13.16, 3.6.4
- `scala-collection-compat` for 2.12 compatibility
- `kind-projector` compiler plugin for 2.12/2.13
- `Outcome` type alias on Scala 3 (OutcomeAlias.scala)
- Version-specific source dirs: `scala-2/` and `scala-3/`

### Changed
- All enums replaced with sealed abstract class + case objects (cross-compatible)
- Shared code uses `IorT` directly instead of `Outcome` type alias
- `MergeStrategy.onConflict` returns `IorT[F, NonEmptyChain[Diagnostic], Resolution]`
- `traverseIor` helper in Merge replaces `.traverse` (2.12 type inference)
- Package object for type aliases (`Pass`, `Lowering`)
- All shared files use brace syntax (2.12 compatible)
- CI/Release workflows: `sbt +compile`, `sbt +test` (cross-build)

## [2.0.0-alpha.1] - 2026-04-04

Complete rewrite from OOP/MLIR-style to pure functional architecture.

### Architecture
- **Pure FP** -- Scala 3 + Cats, tagless final `F[_]`, zero mutation
- **Pass = Kleisli[F, Module, Module]** -- composable via `andThen`
- **Pipeline = function composition** -- `Pipeline.of(pass1, pass2, ...)`
- **Outcome = IorT** -- Right (success), Both (warnings), Left (error)
- **Two-phase emit** -- IR -> CodeNode tree -> text via Renderer

### Core IR (language-agnostic)
- `Module`, `CompilationUnit`, `Decl` (TypeDecl, EnumDecl, FuncDecl, AliasDecl, ConstDecl)
- `TypeKind` -- Product, Protocol, Abstract, Sum, Singleton
- `TypeExpr` -- primitives, collections, generics, union, intersection, function types
- `Expr`, `Stmt`, `Body` -- expression language for method bodies
- `Stmt.Match` + `Pattern` ADT -- language-agnostic pattern matching
- `Meta` -- typed metadata with identity-based keys (vault-style)
- `Doc` -- structured documentation via Meta

### Emitter system
- `LanguageSyntax` trait -- ~30 hooks parameterize BaseEmitter for any language
- `BaseEmitter` -- generic traversal, language-specific via LanguageSyntax
- `CodeNode` tree -- TypeBlock, Func, IfElse, MatchBlock, Comment, etc.
- `Renderer` -- pure, stack-safe (Eval), handles indentation

### Built-in emitters
- **Java** -- JavaEmitter + JavaSyntax + JavaTypeMapping
- **Scala 3** -- ScalaEmitter + ScalaSyntax + ScalaTypeMapping (trait, enum, def, val, match, Option[T])
- **Scala 2** -- ScalaSyntax with sealed trait, case object, new keyword, if (cond)
- `ScalaEmitterConfig` -- scalaVersion, enumStyle, useNewKeyword

### Dialects
- **Proto** -- ProtoFile/ProtoMessage/ProtoField ADT + ProtoLowering

### Merge
- `Merge.merge` with `MergeStrategy[F]` + `Outcome`
- Conflict detection: FieldType, FuncReturnType, Missing
- Resolution: UseType, DualAccessor, Custom, Skip

### IO
- `CodeWriter` -- parallel writes with atomic temp + rename via Resource
- `IncrementalWriter` -- SHA-256 content hashing, skip unchanged files

### Removed (from v1)
- `Operation` trait, `GreenNode`, `NodeKind`, `Region` -- replaced by ADT
- `AttributeMap` -- replaced by `Meta` (typed keys)
- `Pass` trait, `PassContext`, `PassResult` -- replaced by Kleisli
- `Dialect` trait, `GenericDialect`, `GenericOp` -- dialect = user's ADT
- `InterfaceOp`, `ClassOp`, `MethodOp` -- replaced by TypeDecl, Func, Field
- `ContentHash`, `RedTree` -- Scala case classes give immutability/equality for free
- `IrcraftSchema`, `IrcraftCodec` -- removed
- `WriterT`/`Pipe` -- replaced by Outcome (IorT)
- `DiagnosticError` (RuntimeException) -- replaced by Outcome.Left

## [1.0.0-final] - 2026-04-04

Final v1 release (OOP/MLIR-style). Preserved as tag for reference.

## [0.1.0] - 2025-11-05

Initial release.
