package io.alnovis.ircraft.java

import cats.Id
import io.alnovis.ircraft.dialects.proto.{ ProtoFile, ProtoLowering }

import java.util.{ List => JList }
import scala.jdk.CollectionConverters._

/**
  * Java-friendly facade for lowering Protocol Buffer definitions into the ircraft IR.
  *
  * <p>This object provides static methods that delegate to the Scala
  * {@code ProtoLowering} implementation, converting the result into Java-friendly
  * types ({@link IrModule}, {@link Result}).</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import io.alnovis.ircraft.dialects.proto.ProtoFile;
  * import java.util.List;
  *
  * // Lower a single proto file
  * ProtoFile protoFile = ...;  // parsed .proto descriptor
  * Result<IrModule> result = ProtoLoweringFacade.lower(protoFile);
  * if (result.isSuccess()) {
  *     IrModule module = result.value();
  *     // pass to IrEmitter, IrPass pipeline, etc.
  * }
  *
  * // Lower multiple proto files at once
  * List<ProtoFile> files = List.of(file1, file2, file3);
  * Result<IrModule> merged = ProtoLoweringFacade.lowerAll(files);
  * }}}
  *
  * @see [[IrLowering]]  for custom lowering implementations
  * @see [[IrModule]]     for the resulting IR structure
  * @see [[IrEmitter]]    for emitting code from the IR
  * @see [[IrPass]]       for transforming the IR before emission
  */
object ProtoLoweringFacade {

  /**
    * Lowers a single Protocol Buffer file definition into an IR module.
    *
    * @param file the parsed protobuf file descriptor
    * @return a {@link Result} containing the lowered {@link IrModule}
    */
  def lower(file: ProtoFile): Result[IrModule] =
    Result.ok(IrModule.fromScala(ProtoLowering.lower[Id](file)))

  /**
    * Lowers multiple Protocol Buffer file definitions into a single IR module.
    *
    * <p>All files are merged into one module. Use this when processing an entire
    * protobuf package or service definition spanning multiple {@code .proto} files.</p>
    *
    * @param files the list of parsed protobuf file descriptors
    * @return a {@link Result} containing the merged {@link IrModule}
    */
  def lowerAll(files: JList[ProtoFile]): Result[IrModule] =
    Result.ok(IrModule.fromScala(ProtoLowering.lowerAll[Id](files.asScala.toVector)))
}
