# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### FP-MLIR Architecture -- Open-world extensible IR

Functor-based IR with Data Types a la Carte. New dialect = enum F[+A] + Traverse + DialectInfo + trait mixins + lowering algebra. Zero core changes required.

### Added
- **Fix[F]** -- fixpoint type for recursive IR trees
- **Coproduct[F, G, A]** / **:+:** -- disjoint union of dialect functors (split scala-2/3)
- **Inject[F, G]** -- type-safe injection into coproducts (split scala-2/3)
- **scheme.cata / ana / hylo** -- stack-safe recursion schemes via Eval
- **eliminate.dialect[F, G]** -- type-safe coproduct shrinking (e.g., ProtoF :+: SemanticF -> SemanticF)
- **SemanticF[+A]** -- core IR refactored to a functor (TypeDeclF, EnumDeclF, FuncDeclF, AliasDeclF, ConstDeclF)
- **Module[D]** -- parameterized over declaration type
- **Trait mixins** -- HasName, HasFields, HasMethods, HasNested, HasMeta, HasVisibility
- **Coproduct auto-derivation** for all trait mixins (split scala-2/3)
- **Generic passes** via cata + trait constraints (collectAllNames, validateNoEmptyNames)
- **DialectInfo[F]** typeclass -- dialect name + operation count
- **Traverse[F]** / **Functor[F]** infrastructure (Traverse extends Functor)
- **ProtoF[+A]** -- proto dialect as functor (MessageNodeF, EnumNodeF, OneofNodeF) with all 6 trait mixins
- **ProtoLowering.lowerToMixed** -- generic over IR via Inject
- **Constraint system** -- Constraint, ConstraintVerify[C, A], Constrained[A, C] wrapper, !> infix type (Scala 3)
- **MustBeResolved**, **MustNotBeEmpty** built-in constraints
- **ConstraintVerifier** -- verification via cata (verifyFieldTypes, verifyNames)
- SQL community dialect example (SqlF[+A]) proving zero-core-change extensibility
- Cross-compilation: Scala 2.12.20, 2.13.16, 3.6.4
- `scala-collection-compat` for 2.12, `kind-projector` for 2.12/2.13
- `Outcome` type alias on Scala 3 (OutcomeAlias.scala)
- Version-specific source dirs: `scala-2/` and `scala-3/`
- `LanguageSyntax.aliasDecl` -- type alias emission (Scala: `type X = Y`, Java: skip)
- **ircraft-java-api** module -- Java facade hiding Fix/Kleisli/IorT behind idiomatic Java API:
  - `Result<T>` -- three-state result (ok / withWarnings / error), map/flatMap
  - `IrMeta` -- wrapper over opaque Meta with get/set/remove
  - `IrNode` -- Fix[SemanticF] wrapper with static factories and typed views
  - `IrModule` / `IrCompilationUnit` -- Module wrappers
  - `IrPass` / `IrPipeline` -- FunctionalInterface pass composition
  - `IrVisitor<T>` -- bottom-up tree visitor (replaces scheme.cata)
  - `IrLowering<S>` / `ProtoLoweringFacade` -- source-to-IR conversion
  - `IrEmitter` -- java/scala3/scala2 emission facades
  - `IrMerge` / `IrMergeStrategy` -- multi-version merging with built-in strategies
- Documentation: Scala 3 API Guide, Scala 2 API Guide, Java API Guide
- Comprehensive Scaladoc on all 61+ source files across all modules

### Changed
- `Decl` sealed trait -> `SemanticF[+A]` functor with smart constructors + extractors
- `Module[D]` parameterized (was `Module` with fixed `Decl`)
- All consumers updated: Pass, Pipeline, Lowering, Merge, BaseEmitter, ImportCollector, ProtoLowering, emitters
- `scheme.ana` now stack-safe via Eval (constraint: Traverse instead of Functor)
- `emitMatchAsIfChain` delegates to `emitStmtNode` (handles all statement types)
- All enums replaced with sealed abstract class + case objects (cross-compatible)
- Shared code uses `IorT` directly instead of `Outcome` type alias
- `MergeStrategy.onConflict` returns `IorT[F, NonEmptyChain[Diagnostic], Resolution]`
- CI/Release workflows: `sbt +compile`, `sbt +test` (cross-build)

### Fixed
- `scheme.ana` stack overflow on deep trees (>5k levels) -- added Eval trampolining
- `AliasDeclF` silently dropped during emission -- now emits `type X = Y` for Scala
- `emitMatchAsIfChain` dropped Let/Assign/If/ForEach statements -- now handles all via emitStmtNode
- Redundant `Functor` instances in Scala 2 (Coproduct, SemanticF, ProtoF) -- removed, Traverse provides Functor

### Removed
- Redundant `coproductFunctor`, `semanticFunctor`, `protoFunctor` implicit instances (Scala 2)

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
