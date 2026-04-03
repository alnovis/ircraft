package io.alnovis.ircraft.core.merge

import cats.*
import cats.data.NonEmptyVector
import cats.syntax.all.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*

/** A detected conflict between versions. */
case class Conflict(
  declName: String,
  memberName: String,
  kind: ConflictKind,
  versions: NonEmptyVector[(String, TypeExpr)],
  meta: Meta
)

enum ConflictKind:
  case FieldType
  case FuncReturnType
  case Missing

/** User's resolution for a conflict. */
sealed trait Resolution

object Resolution:
  case class UseType(typeExpr: TypeExpr) extends Resolution
  case class DualAccessor(types: Map[String, TypeExpr]) extends Resolution
  case class Custom(decls: Vector[Decl]) extends Resolution
  case object Skip extends Resolution

/** User-provided strategy for resolving conflicts. Uses Outcome for warnings/errors. */
trait MergeStrategy[F[_]]:
  def onConflict(conflict: Conflict): Outcome[F, Resolution]

object Merge:

  object Keys:
    val presentIn: Meta.Key[Vector[String]]            = Meta.Key("merge.presentIn")
    val conflictType: Meta.Key[String]                 = Meta.Key("merge.conflictType")
    val typePerVersion: Meta.Key[Map[String, TypeExpr]] = Meta.Key("merge.typePerVersion")
    val sources: Meta.Key[Vector[String]]              = Meta.Key("merge.sources")

  /** Merge N versioned modules into one. Warnings accumulate, errors stop. */
  def merge[F[_]: Monad](
    versions: NonEmptyVector[(String, Module)],
    strategy: MergeStrategy[F]
  ): Outcome[F, Module] =
    val versionNames = versions.map(_._1).toVector
    val allUnits = versions.toVector.flatMap { (vn, m) =>
      m.units.map(u => (vn, u))
    }
    val byNamespace = allUnits.groupBy(_._2.namespace)

    byNamespace.toVector.traverse { (ns, vUnits) =>
      mergeUnits(ns, vUnits, versionNames, strategy)
    }.map { units =>
      Module(versionNames.mkString("+"), units, Meta.empty.set(Keys.sources, versionNames))
    }

  private def mergeUnits[F[_]: Monad](
    namespace: String,
    versionedUnits: Vector[(String, CompilationUnit)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Outcome[F, CompilationUnit] =
    val allDecls = versionedUnits.flatMap { (v, u) =>
      u.declarations.map(d => (v, d))
    }
    val byName = allDecls.groupBy((_, d) => d.name)

    byName.toVector.traverse { (name, vDecls) =>
      mergeDecl(name, vDecls, allVersions, strategy)
    }.map(decls => CompilationUnit(namespace, decls.flatten))

  private def mergeDecl[F[_]: Monad](
    name: String,
    versionedDecls: Vector[(String, Decl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Outcome[F, Option[Decl]] =
    versionedDecls match
      case Vector((v, single)) =>
        val meta = single match
          case td: Decl.TypeDecl => td.meta.set(Keys.presentIn, Vector(v))
          case _                 => Meta.empty.set(Keys.presentIn, Vector(v))
        Outcome.ok(Some(single.withMeta(meta)))

      case multiple =>
        val presentVersions = multiple.map(_._1)
        val decls = multiple.map(_._2)

        val allTypeDecls = decls.collect { case td: Decl.TypeDecl => td }
        val allEnumDecls = decls.collect { case ed: Decl.EnumDecl => ed }

        if allTypeDecls.size == decls.size then
          val typed = multiple.collect { case (v, td: Decl.TypeDecl) => (v, td) }
          mergeTypeDecls(name, typed, allVersions, strategy).map(Some(_))
        else if allEnumDecls.size == decls.size then
          val typed = multiple.collect { case (v, ed: Decl.EnumDecl) => (v, ed) }
          Outcome.ok(Some(mergeEnumDecls(typed, presentVersions)))
        else
          Outcome.warn(s"Mixed declaration kinds for '$name', using first", Some(decls.head))

  private def mergeTypeDecls[F[_]: Monad](
    name: String,
    versioned: Vector[(String, Decl.TypeDecl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Outcome[F, Decl] =
    val presentVersions = versioned.map(_._1)
    val first = versioned.head._2

    val allFuncs = versioned.flatMap { (v, td) => td.functions.map(f => (v, f)) }
    val funcsByName = allFuncs.groupBy(_._2.name)

    val allFieldEntries = versioned.flatMap { (v, td) => td.fields.map(f => (v, f)) }
    val fieldsByName = allFieldEntries.groupBy(_._2.name)

    val allNestedEntries = versioned.flatMap { (v, td) => td.nested.map(d => (v, d)) }
    val nestedByName = allNestedEntries.groupBy((_, d) => d.name)

    for
      mergedFuncs   <- funcsByName.toVector.traverse { (fname, vFuncs) =>
                         mergeFunctions(name, fname, vFuncs, strategy)
                       }
      mergedFields  <- fieldsByName.toVector.traverse { (fname, vFields) =>
                         mergeFields(name, fname, vFields, strategy)
                       }
      mergedNested  <- nestedByName.toVector.traverse { (nname, vNested) =>
                         mergeDecl(nname, vNested, allVersions, strategy)
                       }
    yield
      val mergedSupertypes = versioned.flatMap(_._2.supertypes).distinct
      val meta = first.meta
        .set(Keys.presentIn, presentVersions)
        .set(Keys.sources, allVersions)

      Decl.TypeDecl(
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

  private def mergeFields[F[_]: Monad](
    declName: String,
    fieldName: String,
    versioned: Vector[(String, Field)],
    strategy: MergeStrategy[F]
  ): Outcome[F, Option[Field]] =
    val first = versioned.head._2
    val types = versioned.map((v, f) => (v, f.fieldType)).distinctBy(_._2)
    if types.size <= 1 then
      Outcome.ok(Some(first))
    else
      val conflict = Conflict(declName, fieldName, ConflictKind.FieldType,
        NonEmptyVector.fromVectorUnsafe(versioned.map((v, f) => (v, f.fieldType))), Meta.empty)
      strategy.onConflict(conflict).map {
        case Resolution.UseType(t) => Some(first.copy(fieldType = t))
        case Resolution.Skip       => None
        case _                     => Some(first)
      }

  private def mergeFunctions[F[_]: Monad](
    declName: String,
    funcName: String,
    versioned: Vector[(String, Func)],
    strategy: MergeStrategy[F]
  ): Outcome[F, Option[Func]] =
    val presentIn = versioned.map(_._1)
    val first = versioned.head._2
    val returnTypes = versioned.map((v, f) => (v, f.returnType)).distinctBy(_._2)

    if returnTypes.size <= 1 then
      val meta = first.meta.set(Keys.presentIn, presentIn)
      Outcome.ok(Some(first.copy(meta = meta)))
    else
      val conflict = Conflict(declName, funcName, ConflictKind.FuncReturnType,
        NonEmptyVector.fromVectorUnsafe(versioned.map((v, f) => (v, f.returnType))), Meta.empty)
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

  @scala.annotation.nowarn("msg=unused explicit parameter")
  private def mergeEnumDecls(
    versioned: Vector[(String, Decl.EnumDecl)],
    presentVersions: Vector[String]
  ): Decl =
    val first = versioned.head._2
    val allVariants = versioned.flatMap(_._2.variants).distinctBy(_.name)
    val meta = first.meta.set(Keys.presentIn, presentVersions)
    first.copy(variants = allVariants, meta = meta)
