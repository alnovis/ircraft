package io.alnovis.ircraft.core.merge

import cats._
import cats.data.{ Ior, IorT, NonEmptyChain, NonEmptyVector }
import cats.syntax.all._
import scala.collection.compat._
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/**
  * A merge conflict detected when combining declarations from multiple versions.
  *
  * When the same declaration (identified by `declName`) has a member (field or function,
  * identified by `memberName`) whose type differs across versions, a [[Conflict]]
  * is raised and handed to the [[MergeStrategy]] for resolution.
  *
  * @param declName   the name of the containing declaration (e.g., class or message name)
  * @param memberName the name of the conflicting member (field name or function name)
  * @param kind       the kind of conflict (field type, function return type, or missing)
  * @param versions   the type expression for the member in each version, as `(versionName, typeExpr)` pairs
  * @param meta       additional metadata associated with this conflict
  * @see [[ConflictKind]] for the possible conflict classifications
  * @see [[MergeStrategy]] for how conflicts are resolved
  * @see [[Resolution]] for possible resolution outcomes
  */
case class Conflict(
  declName: String,
  memberName: String,
  kind: ConflictKind,
  versions: NonEmptyVector[(String, TypeExpr)],
  meta: Meta
)

/**
  * Classification of a merge conflict.
  *
  * @see [[Conflict]] for the conflict type that carries a [[ConflictKind]]
  */
sealed abstract class ConflictKind

/** Companion containing the conflict kind variants. */
object ConflictKind {

  /** A field's type differs across versions. */
  case object FieldType extends ConflictKind

  /** A function's return type differs across versions. */
  case object FuncReturnType extends ConflictKind

  /** A member is present in some versions but missing in others. */
  case object Missing extends ConflictKind
}

/**
  * A resolution decision for a merge [[Conflict]].
  *
  * The [[MergeStrategy]] returns a [[Resolution]] to indicate how the conflict
  * should be handled. The merge algorithm then applies the resolution to produce
  * the merged declaration.
  *
  * @see [[MergeStrategy]] for the conflict resolution callback
  * @see [[Conflict]] for the conflict that triggers resolution
  */
sealed trait Resolution

/** Companion containing the resolution variants. */
object Resolution {

  /**
    * Resolve the conflict by choosing a single unified type for the member.
    *
    * @param typeExpr the type to use in the merged output
    */
  case class UseType(typeExpr: TypeExpr) extends Resolution

  /**
    * Resolve the conflict by generating dual accessors (one per version).
    *
    * @param types a map from version name to the type expression for that version
    */
  case class DualAccessor(types: Map[String, TypeExpr]) extends Resolution

  /**
    * Resolve the conflict by providing fully custom declarations.
    *
    * @param decls the custom declaration trees to use instead of the merged result
    */
  case class Custom(decls: Vector[Fix[SemanticF]]) extends Resolution

  /** Resolve the conflict by skipping (dropping) the conflicting member entirely. */
  case object Skip extends Resolution
}

/**
  * Strategy for resolving merge conflicts encountered during multi-version merging.
  *
  * Implementations receive a [[Conflict]] describing the disagreement and must return
  * a [[Resolution]] wrapped in an `IorT` to allow accumulating warnings alongside
  * the resolution decision.
  *
  * {{{
  * val strategy: MergeStrategy[Id] = new MergeStrategy[Id] {
  *   def onConflict(conflict: Conflict): IorT[Id, NonEmptyChain[Diagnostic], Resolution] =
  *     Outcome.ok[Id, Resolution](Resolution.Skip)
  * }
  * }}}
  *
  * @tparam F the effect type
  * @see [[Conflict]] for the conflict description
  * @see [[Resolution]] for the possible outcomes
  * @see [[Merge.merge]] for the merge entry point that uses this strategy
  */
trait MergeStrategy[F[_]] {

  /**
    * Invoked when a merge conflict is detected.
    *
    * @param conflict the conflict description
    * @return an `IorT` containing the resolution decision, possibly with accumulated warnings
    */
  def onConflict(conflict: Conflict): IorT[F, NonEmptyChain[Diagnostic], Resolution]
}

/**
  * Multi-version merge algorithm for semantic IR modules.
  *
  * [[Merge]] combines multiple versioned modules into a single unified module,
  * detecting and resolving conflicts where declarations differ across versions.
  * The merge operates at three levels:
  *
  *  1. '''Module level''': groups compilation units by namespace
  *  2. '''Compilation unit level''': groups declarations by name
  *  3. '''Declaration level''': merges fields, functions, nested types, and enum variants
  *
  * Conflicts (e.g., a field with different types in different versions) are delegated
  * to a [[MergeStrategy]] for resolution.
  *
  * The merged module's metadata records which versions contributed to each declaration
  * via [[Merge.Keys.presentIn]] and [[Merge.Keys.sources]].
  *
  * @see [[MergeStrategy]] for conflict resolution callbacks
  * @see [[Conflict]] for conflict descriptions
  * @see [[Resolution]] for conflict resolution outcomes
  */
object Merge {

  private type Diags = NonEmptyChain[Diagnostic]

  /**
    * Metadata keys used by the merge algorithm to annotate merged declarations.
    */
  object Keys {

    /** Versions in which a specific declaration or member was present. */
    val presentIn: Meta.Key[Vector[String]] = Meta.Key("merge.presentIn")

    /** The kind of conflict resolution applied (e.g., "RESOLVED", "DUAL_ACCESSOR"). */
    val conflictType: Meta.Key[String] = Meta.Key("merge.conflictType")

    /** A map from version name to the type expression for that version (used with dual-accessor resolution). */
    val typePerVersion: Meta.Key[Map[String, TypeExpr]] = Meta.Key("merge.typePerVersion")

    /** All version names that participated in the merge (set on the merged module). */
    val sources: Meta.Key[Vector[String]] = Meta.Key("merge.sources")
  }

  /**
    * Merges multiple versioned modules into a single unified module.
    *
    * Declarations with the same name across versions are merged, with conflicts
    * delegated to the provided [[MergeStrategy]]. The resulting module's name
    * is the concatenation of all version names (e.g., "v1+v2+v3").
    *
    * {{{
    * val versions = NonEmptyVector.of(
    *   ("v1", moduleV1),
    *   ("v2", moduleV2)
    * )
    * val merged: IorT[Id, Diags, Module[Fix[SemanticF]]] =
    *   Merge.merge[Id](versions, myStrategy)
    * }}}
    *
    * @tparam F the effect type (must have a [[cats.Monad]] instance)
    * @param versions a non-empty vector of `(versionName, module)` pairs
    * @param strategy the conflict resolution strategy
    * @return the merged module wrapped in an `IorT` with accumulated diagnostics
    */
  def merge[F[_]: Monad](
    versions: NonEmptyVector[(String, Module[Fix[SemanticF]])],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, Module[Fix[SemanticF]]] = {
    val versionNames = versions.map(_._1).toVector
    val allUnits = versions.toVector.flatMap {
      case (vn, m) =>
        m.units.map(u => (vn, u))
    }
    val byNamespace = allUnits.groupBy(_._2.namespace)

    traverseIor(byNamespace.toVector) {
      case (ns, vUnits) =>
        mergeUnits(ns, vUnits, versionNames, strategy)
    }
      .map { units =>
        Module(versionNames.mkString("+"), units, Meta.empty.set(Keys.sources, versionNames))
      }
  }

  private def mergeUnits[F[_]: Monad](
    namespace: String,
    versionedUnits: Vector[(String, CompilationUnit[Fix[SemanticF]])],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, CompilationUnit[Fix[SemanticF]]] = {
    val allDecls = versionedUnits.flatMap {
      case (v, u) =>
        u.declarations.map(d => (v, d))
    }
    val byName = allDecls.groupBy { case (_, d) => SemanticF.name(d.unfix) }

    traverseIor(byName.toVector) {
      case (name, vDecls) =>
        mergeDecl(name, vDecls, allVersions, strategy)
    }
      .map(decls => CompilationUnit(namespace, decls.flatten))
  }

  private def mergeDecl[F[_]: Monad](
    name: String,
    versionedDecls: Vector[(String, Fix[SemanticF])],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, Option[Fix[SemanticF]]] = {
    versionedDecls match {
      case Vector((v, single)) =>
        val m = SemanticF.meta(single.unfix).set(Keys.presentIn, Vector(v))
        Outcome.ok(Some(Fix(SemanticF.withMeta(single.unfix, m))))

      case multiple =>
        val presentVersions = multiple.map(_._1)
        val decls           = multiple.map(_._2)

        val allTypeDecls = decls.flatMap { d =>
          d.unfix match {
            case td: TypeDeclF[Fix[SemanticF] @unchecked] => Some(td)
            case _                                        => None
          }
        }
        val allEnumDecls = decls.flatMap { d =>
          d.unfix match {
            case ed: EnumDeclF[Fix[SemanticF] @unchecked] => Some(ed)
            case _                                        => None
          }
        }

        if (allTypeDecls.size == decls.size) {
          val typed = multiple.flatMap {
            case (v, d) =>
              d.unfix match {
                case td: TypeDeclF[Fix[SemanticF] @unchecked] => Some((v, td))
                case _                                        => None
              }
          }
          mergeTypeDecls(name, typed, allVersions, strategy).map(Some(_))
        } else if (allEnumDecls.size == decls.size) {
          val typed = multiple.flatMap {
            case (v, d) =>
              d.unfix match {
                case ed: EnumDeclF[Fix[SemanticF] @unchecked] => Some((v, ed))
                case _                                        => None
              }
          }
          Outcome.ok(Some(mergeEnumDecls(typed, presentVersions)))
        } else {
          Outcome.warn(s"Mixed declaration kinds for '$name', using first", Some(decls.head))
        }
    }
  }

  private def mergeTypeDecls[F[_]: Monad](
    name: String,
    versioned: Vector[(String, TypeDeclF[Fix[SemanticF]])],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, Fix[SemanticF]] = {
    val presentVersions = versioned.map(_._1)
    val first           = versioned.head._2

    val allFuncs    = versioned.flatMap { case (v, td) => td.functions.map(f => (v, f)) }
    val funcsByName = allFuncs.groupBy(_._2.name)

    val allFieldEntries = versioned.flatMap { case (v, td) => td.fields.map(f => (v, f)) }
    val fieldsByName    = allFieldEntries.groupBy(_._2.name)

    val allNestedEntries = versioned.flatMap { case (v, td) => td.nested.map(d => (v, d)) }
    val nestedByName     = allNestedEntries.groupBy { case (_, d) => SemanticF.name(d.unfix) }

    for {
      mergedFuncs <- traverseIor(funcsByName.toVector) {
        case (fname, vFuncs) =>
          mergeFunctions(name, fname, vFuncs, strategy)
      }
      mergedFields <- traverseIor(fieldsByName.toVector) {
        case (fname, vFields) =>
          mergeFields(name, fname, vFields, strategy)
      }
      mergedNested <- traverseIor(nestedByName.toVector) {
        case (nname, vNested) =>
          mergeDecl(nname, vNested, allVersions, strategy)
      }
    } yield {
      val mergedSupertypes = versioned.flatMap(_._2.supertypes).distinct
      val meta = first.meta
        .set(Keys.presentIn, presentVersions)
        .set(Keys.sources, allVersions)

      Fix[SemanticF](
        TypeDeclF(
          name = name,
          kind = first.kind,
          fields = mergedFields.flatten,
          functions = mergedFuncs.flatten,
          nested = mergedNested.flatten,
          supertypes = mergedSupertypes,
          typeParams = first.typeParams,
          visibility = first.visibility,
          annotations = first.annotations,
          meta = meta
        )
      )
    }
  }

  private def mergeFields[F[_]: Monad](
    declName: String,
    fieldName: String,
    versioned: Vector[(String, Field)],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, Option[Field]] = {
    val first = versioned.head._2
    val types = versioned.map { case (v, f) => (v, f.fieldType) }.distinctBy(_._2)
    if (types.size <= 1) {
      Outcome.ok(Some(first))
    } else {
      val conflict = Conflict(
        declName,
        fieldName,
        ConflictKind.FieldType,
        NonEmptyVector.fromVectorUnsafe(versioned.map { case (v, f) => (v, f.fieldType) }),
        Meta.empty
      )
      strategy.onConflict(conflict).map {
        case Resolution.UseType(t) => Some(first.copy(fieldType = t))
        case Resolution.Skip       => None
        case _                     => Some(first)
      }
    }
  }

  private def mergeFunctions[F[_]: Monad](
    declName: String,
    funcName: String,
    versioned: Vector[(String, Func)],
    strategy: MergeStrategy[F]
  ): IorT[F, Diags, Option[Func]] = {
    val presentIn   = versioned.map(_._1)
    val first       = versioned.head._2
    val returnTypes = versioned.map { case (v, f) => (v, f.returnType) }.distinctBy(_._2)

    if (returnTypes.size <= 1) {
      val meta = first.meta.set(Keys.presentIn, presentIn)
      Outcome.ok(Some(first.copy(meta = meta)))
    } else {
      val conflict = Conflict(
        declName,
        funcName,
        ConflictKind.FuncReturnType,
        NonEmptyVector.fromVectorUnsafe(versioned.map { case (v, f) => (v, f.returnType) }),
        Meta.empty
      )
      strategy.onConflict(conflict).map {
        case Resolution.UseType(t) =>
          val meta = first.meta
            .set(Keys.presentIn, presentIn)
            .set(Keys.conflictType, "RESOLVED")
          Some(first.copy(returnType = t, meta = meta))
        case Resolution.DualAccessor(types) =>
          val meta = first.meta
            .set(Keys.presentIn, presentIn)
            .set(Keys.conflictType, "DUAL_ACCESSOR")
            .set(Keys.typePerVersion, types)
          Some(first.copy(meta = meta))
        case Resolution.Custom(_) =>
          val meta = first.meta.set(Keys.presentIn, presentIn)
          Some(first.copy(meta = meta))
        case Resolution.Skip => None
      }
    }
  }

  @scala.annotation.nowarn("msg=unused explicit parameter")
  private def mergeEnumDecls(
    versioned: Vector[(String, EnumDeclF[Fix[SemanticF]])],
    presentVersions: Vector[String]
  ): Fix[SemanticF] = {
    val first       = versioned.head._2
    val allVariants = versioned.flatMap(_._2.variants).distinctBy(_.name)
    val meta        = first.meta.set(Keys.presentIn, presentVersions)
    Fix[SemanticF](first.copy(variants = allVariants, meta = meta))
  }

  /** Helper: traverse with IorT that works on Scala 2.12 (avoids type inference issues). */
  private def traverseIor[F[_]: Monad, A, B](
    items: Vector[A]
  )(f: A => IorT[F, Diags, B]): IorT[F, Diags, Vector[B]] = {
    val empty: IorT[F, Diags, Vector[B]] = IorT.fromIor(Ior.Right(Vector.empty[B]))
    items.foldLeft(empty) { (acc, item) =>
      for {
        xs <- acc
        x  <- f(item)
      } yield xs :+ x
    }
  }
}
