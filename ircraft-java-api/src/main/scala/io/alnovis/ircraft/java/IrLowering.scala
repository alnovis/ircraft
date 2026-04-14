package io.alnovis.ircraft.java

/**
  * A lowering operation that converts a source representation of type {@code S} into
  * an {@link IrModule}, wrapped in a {@link Result}.
  *
  * <p>This is the Java-friendly replacement for the Scala {@code Kleisli[F, Source, Module]}
  * pattern. Lowering is the first stage of the ircraft pipeline: it takes external data
  * (e.g., parsed protobuf descriptors, OpenAPI specs) and produces the IR.</p>
  *
  * <p>{@code IrLowering} is a functional interface, so it can be implemented as a lambda in Java.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * // Custom lowering from a domain-specific source
  * IrLowering<MySchema> myLowering = schema -> {
  *     IrModule module = convertToIr(schema);
  *     return Result.ok(module);
  * };
  *
  * // Pure lowering (no diagnostics)
  * IrLowering<MySchema> pureLowering = IrLowering.pure(schema -> convertToIr(schema));
  *
  * Result<IrModule> result = myLowering.lower(inputSchema);
  * }}}
  *
  * @tparam S the source type to lower from
  * @see [[ProtoLoweringFacade]] for the built-in protobuf lowering
  * @see [[IrPass]]              for transforming modules after lowering
  * @see [[Result]]              for the three-state result type
  */
@FunctionalInterface
trait IrLowering[S] {

  /**
    * Lowers the given source value into an IR module.
    *
    * @param source the source representation to lower
    * @return a {@link Result} containing the lowered {@link IrModule}, possibly with
    *         warnings or errors
    */
  def lower(source: S): Result[IrModule]
}

/**
  * Factory methods for creating {@link IrLowering} instances.
  *
  * @see [[IrLowering]]
  */
object IrLowering {

  /**
    * Creates a pure lowering that always succeeds with no diagnostics.
    *
    * <p>Use this when the conversion from source to IR cannot fail.</p>
    *
    * <h3>Usage from Java</h3>
    * {{{
    * IrLowering<MySchema> lowering = IrLowering.pure(schema -> {
    *     // build IrModule from schema...
    *     return IrModule.of("my-module", compilationUnit);
    * });
    * }}}
    *
    * @param f   the conversion function from source to {@link IrModule}
    * @tparam S  the source type
    * @return a new lowering that wraps the function's result in {@code Result.ok}
    */
  def pure[S](f: java.util.function.Function[S, IrModule]): IrLowering[S] =
    source => Result.ok(f.apply(source))
}
