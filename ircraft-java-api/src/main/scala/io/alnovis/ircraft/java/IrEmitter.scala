package io.alnovis.ircraft.java

import cats.Id
import io.alnovis.ircraft.emitters.java.JavaEmitter
import io.alnovis.ircraft.emitters.scala.ScalaEmitter

import java.nio.file.Path
import java.util.{ Map => JMap }
import scala.jdk.CollectionConverters._

/**
  * Java-friendly facade for the built-in code emitters.
  *
  * <p>Each method takes an {@link IrModule} and produces a map from output file paths
  * to generated source code strings. The emitters support Java, Scala 3, and Scala 2
  * target languages.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrModule module = ...;  // from lowering + passes
  *
  * // Emit Java source files
  * Result<Map<Path, String>> javaFiles = IrEmitter.java(module);
  * if (javaFiles.isSuccess()) {
  *     for (Map.Entry<Path, String> entry : javaFiles.value().entrySet()) {
  *         Path filePath = entry.getKey();
  *         String sourceCode = entry.getValue();
  *         Files.writeString(filePath, sourceCode);
  *     }
  * }
  *
  * // Emit Scala 3 source files
  * Result<Map<Path, String>> scala3Files = IrEmitter.scala3(module);
  *
  * // Emit Scala 2 source files
  * Result<Map<Path, String>> scala2Files = IrEmitter.scala2(module);
  * }}}
  *
  * @see [[IrModule]] for the input IR structure
  * @see [[IrPass]]   for transforming the IR before emission
  * @see [[Result]]    for the three-state result type
  */
object IrEmitter {

  /**
    * Emits Java source code from the given IR module.
    *
    * <p>Generates idiomatic Java classes, enums, and interfaces based on the
    * IR declarations in the module.</p>
    *
    * @param module the IR module to emit
    * @return a {@link Result} containing a map from file paths to Java source code
    */
  def java(module: IrModule): Result[JMap[Path, String]] =
    Result.ok(JavaEmitter[Id].apply(module.toScala).asJava)

  /**
    * Emits Scala 3 source code from the given IR module.
    *
    * <p>Generates idiomatic Scala 3 code using enums, extension methods, and
    * other Scala 3 features where appropriate.</p>
    *
    * @param module the IR module to emit
    * @return a {@link Result} containing a map from file paths to Scala 3 source code
    */
  def scala3(module: IrModule): Result[JMap[Path, String]] =
    Result.ok(ScalaEmitter.scala3[Id].apply(module.toScala).asJava)

  /**
    * Emits Scala 2 source code from the given IR module.
    *
    * <p>Generates Scala 2-compatible code, avoiding Scala 3-specific syntax.</p>
    *
    * @param module the IR module to emit
    * @return a {@link Result} containing a map from file paths to Scala 2 source code
    */
  def scala2(module: IrModule): Result[JMap[Path, String]] =
    Result.ok(ScalaEmitter.scala2[Id].apply(module.toScala).asJava)
}
