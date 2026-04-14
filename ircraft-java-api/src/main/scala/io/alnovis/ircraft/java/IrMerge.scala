package io.alnovis.ircraft.java

import cats.Id
import cats.data.{ IorT, NonEmptyChain, NonEmptyVector }
import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.merge.{ Conflict, Merge, MergeStrategy, Resolution }

import java.util.{ Map => JMap }
import scala.jdk.CollectionConverters._

/**
  * Strategy for resolving conflicts when merging multiple IR module versions.
  *
  * <p>When two or more versions of the same declaration conflict during merging,
  * the merge engine calls {@link #onConflict} to determine how to resolve the
  * conflict. The strategy can choose to use one version's type, skip the
  * declaration, or report an error.</p>
  *
  * <p>{@code IrMergeStrategy} is a functional interface, so it can be implemented
  * as a lambda in Java.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * // Custom strategy: prefer the first version, warn about conflicts
  * IrMergeStrategy myStrategy = conflict -> {
  *     System.out.println("Conflict on: " + conflict.name());
  *     return Result.ok(Resolution.UseType(conflict.versions().head()._2()));
  * };
  *
  * // Or use built-in strategies
  * IrMergeStrategy first = IrMerge.useFirst();
  * IrMergeStrategy last  = IrMerge.useLast();
  * IrMergeStrategy skip  = IrMerge.skip();
  * }}}
  *
  * @see [[IrMerge]]     for the merge operation
  * @see [[Resolution]]  for the possible resolution outcomes
  * @see [[Conflict]]    for details about the conflicting declarations
  */
@FunctionalInterface
trait IrMergeStrategy {

  /**
    * Called when a conflict is detected between two or more declaration versions.
    *
    * <p>The returned {@link Result} can be:</p>
    * <ul>
    *   <li>{@code Result.ok(Resolution.UseType(...))} -- use a specific type version</li>
    *   <li>{@code Result.ok(Resolution.Skip)} -- skip the conflicting declaration entirely</li>
    *   <li>{@code Result.error(...)} -- abort the merge with an error</li>
    * </ul>
    *
    * @param conflict the conflict descriptor with version details
    * @return a {@link Result} containing the chosen {@link Resolution}
    */
  def onConflict(conflict: Conflict): Result[Resolution]
}

/**
  * Java-friendly facade for merging multiple versioned IR modules.
  *
  * <p>Use this when you have multiple versions of the same schema (e.g., protobuf v2 and v3)
  * and need to produce a single unified IR module. The merge operation detects conflicting
  * declarations and delegates to an {@link IrMergeStrategy} for resolution.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import java.util.Map;
  *
  * IrModule v1Module = ...;  // lowered from schema v1
  * IrModule v2Module = ...;  // lowered from schema v2
  *
  * Map<String, IrModule> versions = Map.of("v1", v1Module, "v2", v2Module);
  *
  * // Merge using "use first version on conflict" strategy
  * Result<IrModule> merged = IrMerge.merge(versions, IrMerge.useFirst());
  *
  * if (merged.isSuccess()) {
  *     IrModule unifiedModule = merged.value();
  *     // proceed with passes and emission...
  * }
  * }}}
  *
  * @see [[IrMergeStrategy]] for conflict resolution strategies
  * @see [[IrModule]]        for the module type being merged
  */
object IrMerge {

  /**
    * Merges multiple named module versions into a single unified module.
    *
    * <p>The map must contain at least one entry. Declarations that appear in only one
    * version pass through unchanged. Declarations that appear in multiple versions with
    * different definitions trigger a call to the merge strategy.</p>
    *
    * @param versions a non-empty map from version name to module
    * @param strategy the conflict resolution strategy to use
    * @return a {@link Result} containing the merged module, possibly with warnings;
    *         or an error if the strategy rejects a conflict or the map is empty
    */
  def merge(versions: JMap[String, IrModule], strategy: IrMergeStrategy): Result[IrModule] = {
    val entries = versions.asScala.toVector.map { case (name, m) => (name, m.toScala) }
    NonEmptyVector.fromVector(entries) match {
      case None => Result.error("merge requires at least one version")
      case Some(nev) =>
        val scalaStrategy = new MergeStrategy[Id] {
          def onConflict(conflict: Conflict): IorT[Id, NonEmptyChain[Diagnostic], Resolution] =
            strategy.onConflict(conflict).toOutcome
        }
        Result.fromOutcome(Merge.merge[Id](nev, scalaStrategy)).map[IrModule](IrModule.fromScala(_))
    }
  }

  /**
    * Returns a built-in merge strategy that resolves conflicts by using the first version's type.
    *
    * <p>The "first" version is determined by the iteration order of the map passed to
    * {@link #merge}.</p>
    *
    * @return a merge strategy that always picks the first version
    */
  def useFirst: IrMergeStrategy = conflict => Result.ok(Resolution.UseType(conflict.versions.head._2))

  /**
    * Returns a built-in merge strategy that resolves conflicts by using the last version's type.
    *
    * <p>The "last" version is determined by the iteration order of the map passed to
    * {@link #merge}.</p>
    *
    * @return a merge strategy that always picks the last version
    */
  def useLast: IrMergeStrategy = conflict => Result.ok(Resolution.UseType(conflict.versions.last._2))

  /**
    * Returns a built-in merge strategy that resolves conflicts by skipping the
    * conflicting declaration entirely.
    *
    * <p>The declaration will not appear in the merged module.</p>
    *
    * @return a merge strategy that always skips conflicts
    */
  def skip: IrMergeStrategy = _ => Result.ok(Resolution.Skip)
}
