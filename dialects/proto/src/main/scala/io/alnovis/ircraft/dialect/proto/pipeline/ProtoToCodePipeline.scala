package io.alnovis.ircraft.dialect.proto.pipeline

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.cache.{ IncrementalCache, IncrementalCacheIO }
import io.alnovis.ircraft.core.emit.Emitter
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.proto.ops.SchemaOp
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass

/**
  * Generic pipeline: Proto Schema -> source files via any Emitter.
  *
  * Full pass chain:
  *   1. ProtoVerifierPass - validate proto IR
  *   2. ProtoToSemanticLowering - proto -> semantic (interface + abstract + impl)
  *   3. ConflictResolutionPass - add conflict-specific methods (INT_ENUM, STRING_BYTES, etc.)
  *   4. HasMethodsPass - add has/supports methods
  *   5. ProtoWrapperPass - generate ProtoWrapper base interface
  *   6. CommonMethodsPass - add equals/hashCode/toString/toBytes
  *   7. VersionContextPass - generate VersionContext + per-version impls
  *   8. ProtocolVersionsPass - generate version constants
  *   9. VersionConversionPass - add asVersion/getFieldsInaccessibleInVersion
  *  10. BuilderPass - generate Builder pattern (conditional)
  *  11. WktConversionPass - convert well-known types
  *  12. ValidationAnnotationsPass - add @NotNull etc. (conditional)
  *  13. SchemaMetadataPass - generate SchemaInfo (conditional)
  *
  * @param config
  *   lowering configuration (packages, conditional features)
  * @param emitter
  *   language emitter (DirectJavaEmitter, DirectKotlinEmitter, DirectScalaEmitter, etc.)
  */
class ProtoToCodePipeline(config: LoweringConfig, emitter: Emitter):

  private val pipeline: Pipeline = Pipeline(
    "proto-to-code",
    ProtoVerifierPass,
    ProtoToSemanticLowering(config),
    ConflictResolutionPass,
    HasMethodsPass,
    ProtoWrapperPass,
    CommonMethodsPass,
    VersionContextPass,
    ProtocolVersionsPass,
    VersionConversionPass,
    BuilderPass,
    WktConversionPass,
    ValidationAnnotationsPass,
    SchemaMetadataPass
  )

  /**
    * Run the full pipeline and emit source files.
    *
    * @return
    *   Either errors or a map of file path -> source code
    */
  def execute(
    module: Module,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    val result = pipeline.run(module, context)
    if result.hasErrors then Left(result.errors)
    else Right(emitter.emit(result.module))

  /**
    * Run the pipeline with incremental caching: only return files for changed entities.
    *
    * Files for unchanged entities are skipped. Global files (without sourceEntity) are included whenever any entity has
    * changed. If nothing changed, returns an empty map.
    *
    * @param cacheDir
    *   directory for storing ir-cache.json
    * @return
    *   Either errors or a map of file path -> source code (only changed files)
    */
  def executeIncremental(
    module: Module,
    cacheDir: Path,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    // 1. Extract SchemaOp and compute entity hashes
    val schemaOpt = module.topLevel.collectFirst { case s: SchemaOp => s }
    if schemaOpt.isEmpty then return execute(module, context)
    val schema                          = schemaOpt.get
    val currentHashes: Map[String, Int] = schema.messageHashes ++ schema.enumHashes

    // 2. Load cache and determine changed entities
    val cacheOpt = IncrementalCacheIO.load(cacheDir).filter(_.version == IncrementalCache.CurrentVersion)
    val changed = cacheOpt match
      case Some(cache) =>
        val c = cache.changedEntities(currentHashes)
        if c.isEmpty then return Right(Map.empty) // nothing changed -- all cached
        c
      case None => currentHashes.keySet // no valid cache -- treat all as changed

    // 4. Run full pipeline
    val result = pipeline.run(module, context)
    if result.hasErrors then return Left(result.errors)

    // 5. Emit with mapping
    val (allFiles, fileToEntity) = emitter.emitWithMapping(result.module, Some(ProtoAttributes.SourceEntity))

    // 6. Filter: changed entity files + all global files
    val filteredFiles = allFiles.filter { (path, _) =>
      fileToEntity.get(path) match
        case Some(Some(entity)) => changed.contains(entity)
        case _                  => true // global or unmapped -- always include
    }

    // 7. Save updated cache
    val newCache = currentHashes
      .foldLeft(IncrementalCache.empty) {
        case (cache, (name, hash)) =>
          val entityFiles = allFiles.keySet.filter(p => fileToEntity.get(p).flatten.contains(name))
          cache.update(name, hash, entityFiles)
      }
      .withGlobalFiles(allFiles.keySet.filter(p => fileToEntity.get(p).flatten.isEmpty))
    IncrementalCacheIO.save(cacheDir, newCache)

    Right(filteredFiles)
