package io.alnovis.ircraft.pipeline.prototojava

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.Emitter
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.*
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass

/**
  * Pre-built pipeline: Proto Schema → Java source files.
  *
  * Full pass chain:
  *   1. ProtoVerifierPass — validate proto IR
  *   2. ProtoToSemanticLowering — proto → semantic (interface + abstract + impl)
  *   3. ConflictResolutionPass — add conflict-specific methods (INT_ENUM, STRING_BYTES, etc.)
  *   4. HasMethodsPass — add has/supports methods
  *   5. ProtoWrapperPass — generate ProtoWrapper base interface
  *   6. CommonMethodsPass — add equals/hashCode/toString/toBytes
  *   7. VersionContextPass — generate VersionContext + per-version impls
  *   8. ProtocolVersionsPass — generate version constants
  *   9. VersionConversionPass — add asVersion/getFieldsInaccessibleInVersion
  *  10. BuilderPass — generate Builder pattern (conditional)
  *  11. WktConversionPass — convert well-known types
  *  12. ValidationAnnotationsPass — add @NotNull etc. (conditional)
  *  13. SchemaMetadataPass — generate SchemaInfo (conditional)
  */
class ProtoToJavaPipeline(config: LoweringConfig):

  private val pipeline: Pipeline = Pipeline(
    "proto-to-java",
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
    SchemaMetadataPass,
  )

  private val emitter: Emitter = DirectJavaEmitter()

  /**
    * Run the full pipeline and emit Java source files.
    *
    * @return
    *   Either errors or a map of file path → source code
    */
  def execute(
    module: Module,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    val result = pipeline.run(module, context)
    if result.hasErrors then Left(result.errors)
    else Right(emitter.emit(result.module))
