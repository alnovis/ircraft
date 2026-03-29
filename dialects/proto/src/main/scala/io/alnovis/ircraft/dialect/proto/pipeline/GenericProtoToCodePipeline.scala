package io.alnovis.ircraft.dialect.proto.pipeline

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.cache.{ IncrementalCache, IncrementalCacheIO }
import io.alnovis.ircraft.core.emit.Emitter
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.proto.ops.SchemaOp
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass

/**
  * Generic proto-to-code pipeline with only universal protobuf lowering passes.
  *
  * Contains no proto-wrapper-specific logic (no ProtoWrapper, VersionContext, conflict resolution, etc.). Downstream
  * consumers compose their own pipeline by appending domain-specific passes via [[Pipeline.andThen]].
  *
  * Generic passes:
  *   1. ProtoVerifierPass -- validate proto IR
  *   2. ProtoToSemanticLowering -- proto -> semantic (interface + abstract + impl per version)
  *   3. HasMethodsPass -- add has/supports methods for optional/version-specific fields
  *   4. BuilderPass -- generate Builder pattern (conditional)
  *   5. WktConversionPass -- convert well-known types (conditional)
  *   6. ValidationAnnotationsPass -- add @NotNull etc. (conditional)
  *
  * {{{
  * // Usage from downstream (e.g., proto-wrapper-plugin):
  * val generic = GenericProtoToCodePipeline(config, emitter)
  * val fullPipeline = generic.pipeline
  *   .andThen(myConflictPass)
  *   .andThen(myVersionContextPass)
  * val result = fullPipeline.run(module, context)
  * }}}
  */
class GenericProtoToCodePipeline(config: LoweringConfig, emitter: Emitter):

  /** The generic pipeline -- append domain-specific passes via andThen(). */
  val pipeline: Pipeline = Pipeline(
    "proto-to-code-generic",
    ProtoVerifierPass,
    ProtoToSemanticLowering(config),
    HasMethodsPass,
    BuilderPass,
    WktConversionPass,
    ValidationAnnotationsPass
  )

  /** Run the generic pipeline and emit source files. */
  def execute(
    module: IrModule,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    val result = pipeline.run(module, context)
    if result.hasErrors then Left(result.errors)
    else Right(emitter.emit(result.module))

  /** Run with incremental caching. */
  def executeIncremental(
    module: IrModule,
    cacheDir: Path,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    val schemaOpt = module.topLevel.collectFirst { case s: SchemaOp => s }
    if schemaOpt.isEmpty then return execute(module, context)
    val schema                          = schemaOpt.get
    val currentHashes: Map[String, Int] = schema.messageHashes ++ schema.enumHashes

    val cacheOpt = IncrementalCacheIO.load(cacheDir).filter(_.version == IncrementalCache.CurrentVersion)
    val changed = cacheOpt match
      case Some(cache) =>
        val c = cache.changedEntities(currentHashes)
        if c.isEmpty then return Right(Map.empty)
        c
      case None => currentHashes.keySet

    val result = pipeline.run(module, context)
    if result.hasErrors then return Left(result.errors)

    val (allFiles, fileToEntity) = emitter.emitWithMapping(result.module, Some(ProtoAttributes.SourceEntity))

    val filteredFiles = allFiles.filter { (path, _) =>
      fileToEntity.get(path) match
        case Some(Some(entity)) => changed.contains(entity)
        case _                  => true
    }

    val newCache = currentHashes
      .foldLeft(IncrementalCache.empty) {
        case (cache, (name, hash)) =>
          val entityFiles = allFiles.keySet.filter(p => fileToEntity.get(p).flatten.contains(name))
          cache.update(name, hash, entityFiles)
      }
      .withGlobalFiles(allFiles.keySet.filter(p => fileToEntity.get(p).flatten.isEmpty))
    IncrementalCacheIO.save(cacheDir, newCache)

    Right(filteredFiles)
