package io.alnovis.ircraft.dialect.proto.pipeline

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.Emitter
import io.alnovis.ircraft.dialect.proto.lowering.*
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
