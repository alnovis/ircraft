package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ CompilationUnit, Meta, Module, SemanticF }

import java.util.{ List => JList }
import scala.jdk.CollectionConverters._

/**
  * Java-friendly wrapper over {@code CompilationUnit[Fix[SemanticF]]}.
  *
  * <p>A compilation unit represents a single namespaced group of declarations -- typically
  * corresponding to one source file (e.g., one {@code .proto} file, one {@code .graphql} schema).
  * It has a namespace (package/module name) and a list of declarations.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrNode msg = IrNode.typeDecl("Person", TypeKind.Message());
  * IrNode status = IrNode.enumDecl("Status", List.of(
  *     EnumVariant.of("ACTIVE", 0),
  *     EnumVariant.of("INACTIVE", 1)
  * ));
  *
  * IrCompilationUnit unit = IrCompilationUnit.of("com.example.api", msg, status);
  * String ns = unit.namespace();             // "com.example.api"
  * List<IrNode> decls = unit.declarations(); // [Person, Status]
  * }}}
  *
  * @see [[IrModule]] for grouping multiple compilation units
  * @see [[IrNode]]   for individual declarations
  */
final class IrCompilationUnit private (private val cu: CompilationUnit[Fix[SemanticF]]) {

  /**
    * Returns the namespace (package name) of this compilation unit.
    *
    * @return the namespace string, never {@code null}
    */
  def namespace: String = cu.namespace

  /**
    * Returns the list of IR declarations in this compilation unit.
    *
    * @return an unmodifiable list of {@link IrNode} instances
    */
  def declarations: JList[IrNode] = cu.declarations.map(IrNode.fromScala).asJava

  /**
    * Returns the metadata attached to this compilation unit.
    *
    * @return the metadata; may be empty but never {@code null}
    * @see [[IrMeta]]
    */
  def meta: IrMeta = IrMeta.fromScala(cu.meta)

  /**
    * Returns the underlying Scala {@code CompilationUnit[Fix[SemanticF]]} value.
    *
    * <p>Use this when bridging back to Scala-based ircraft APIs.</p>
    *
    * @return the Scala compilation unit
    */
  def toScala: CompilationUnit[Fix[SemanticF]] = cu

  override def toString: String = s"IrCompilationUnit(${cu.namespace}, ${cu.declarations.size} decls)"
}

/**
  * Factory methods for creating {@link IrCompilationUnit} instances.
  *
  * @see [[IrCompilationUnit]]
  */
object IrCompilationUnit {

  /**
    * Creates a compilation unit from a namespace and varargs declarations.
    *
    * <h3>Usage from Java</h3>
    * {{{
    * IrCompilationUnit unit = IrCompilationUnit.of(
    *     "com.example.api",
    *     IrNode.typeDecl("Request", TypeKind.Message()),
    *     IrNode.typeDecl("Response", TypeKind.Message())
    * );
    * }}}
    *
    * @param namespace    the package/namespace for this unit
    * @param declarations zero or more IR declaration nodes
    * @return a new compilation unit
    */
  def of(namespace: String, declarations: IrNode*): IrCompilationUnit =
    new IrCompilationUnit(CompilationUnit(namespace, declarations.map(_.toScala).toVector))

  /**
    * Creates a compilation unit from a namespace and a Java list of declarations.
    *
    * @param namespace    the package/namespace for this unit
    * @param declarations the list of IR declaration nodes
    * @return a new compilation unit
    */
  def of(namespace: String, declarations: JList[IrNode]): IrCompilationUnit =
    new IrCompilationUnit(CompilationUnit(namespace, declarations.asScala.toVector.map(_.toScala)))

  /**
    * Wraps an existing Scala {@code CompilationUnit[Fix[SemanticF]]} as an {@code IrCompilationUnit}.
    *
    * @param cu the Scala compilation unit
    * @return a new {@code IrCompilationUnit} wrapping the given value
    */
  def fromScala(cu: CompilationUnit[Fix[SemanticF]]): IrCompilationUnit =
    new IrCompilationUnit(cu)
}

/**
  * Java-friendly wrapper over {@code Module[Fix[SemanticF]]}.
  *
  * <p>A module is the top-level IR container, grouping one or more compilation units
  * under a common name. It represents the complete output of a lowering operation
  * (e.g., all declarations from a set of {@code .proto} files).</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrCompilationUnit apiUnit = IrCompilationUnit.of("com.example.api", ...);
  * IrCompilationUnit modelUnit = IrCompilationUnit.of("com.example.model", ...);
  *
  * IrModule module = IrModule.of("my-service", apiUnit, modelUnit);
  *
  * String name = module.name();                    // "my-service"
  * List<IrCompilationUnit> units = module.units(); // [apiUnit, modelUnit]
  * List<IrNode> all = module.allDeclarations();     // flattened from all units
  * }}}
  *
  * @see [[IrCompilationUnit]] for the units within a module
  * @see [[IrPass]]            for transforming modules
  * @see [[IrEmitter]]         for emitting code from modules
  */
final class IrModule private (private val module: Module[Fix[SemanticF]]) {

  /**
    * Returns the name of this module.
    *
    * @return the module name, never {@code null}
    */
  def name: String = module.name

  /**
    * Returns the list of compilation units in this module.
    *
    * @return a list of {@link IrCompilationUnit} instances
    */
  def units: JList[IrCompilationUnit] = module.units.map(IrCompilationUnit.fromScala).asJava

  /**
    * Returns the metadata attached to this module.
    *
    * @return the metadata; may be empty but never {@code null}
    * @see [[IrMeta]]
    */
  def meta: IrMeta = IrMeta.fromScala(module.meta)

  /**
    * Returns all declarations across all compilation units, flattened into a single list.
    *
    * <p>This is a convenience method equivalent to iterating over {@link #units} and
    * collecting all {@link IrCompilationUnit#declarations}.</p>
    *
    * @return a flattened list of all {@link IrNode} instances in this module
    */
  def allDeclarations: JList[IrNode] =
    module.units.flatMap(_.declarations).map(IrNode.fromScala).asJava

  /**
    * Returns the underlying Scala {@code Module[Fix[SemanticF]]} value.
    *
    * <p>Use this when bridging back to Scala-based ircraft APIs.</p>
    *
    * @return the Scala module
    */
  def toScala: Module[Fix[SemanticF]] = module

  override def toString: String = s"IrModule(${module.name}, ${module.units.size} units)"

  override def equals(obj: Any): Boolean = obj match {
    case other: IrModule => module == other.module
    case _               => false
  }

  override def hashCode(): Int = module.hashCode()
}

/**
  * Factory methods for creating {@link IrModule} instances.
  *
  * <h3>Usage from Java</h3>
  * {{{
  * // From varargs compilation units
  * IrModule module = IrModule.of("my-service", unit1, unit2);
  *
  * // From a Java list
  * IrModule module = IrModule.of("my-service", List.of(unit1, unit2));
  *
  * // Empty module (no compilation units)
  * IrModule empty = IrModule.empty("placeholder");
  * }}}
  *
  * @see [[IrModule]]
  */
object IrModule {

  /**
    * Creates a module from a name and varargs compilation units.
    *
    * @param name  the module name
    * @param units zero or more compilation units
    * @return a new module
    */
  def of(name: String, units: IrCompilationUnit*): IrModule =
    new IrModule(Module(name, units.map(_.toScala).toVector))

  /**
    * Creates a module from a name and a Java list of compilation units.
    *
    * @param name  the module name
    * @param units the list of compilation units
    * @return a new module
    */
  def of(name: String, units: JList[IrCompilationUnit]): IrModule =
    new IrModule(Module(name, units.asScala.toVector.map(_.toScala)))

  /**
    * Creates an empty module with no compilation units.
    *
    * @param name the module name
    * @return a new empty module
    */
  def empty(name: String): IrModule =
    new IrModule(Module.empty(name))

  /**
    * Wraps an existing Scala {@code Module[Fix[SemanticF]]} as an {@code IrModule}.
    *
    * @param module the Scala module
    * @return a new {@code IrModule} wrapping the given value
    */
  def fromScala(module: Module[Fix[SemanticF]]): IrModule =
    new IrModule(module)
}
