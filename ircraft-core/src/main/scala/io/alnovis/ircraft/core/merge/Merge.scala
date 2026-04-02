package io.alnovis.ircraft.core.merge

import cats.*
import cats.data.{Chain, NonEmptyVector, WriterT}
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
  case FieldType      // same field, different types
  case FuncReturnType // same function, different return types
  case Missing        // member exists in some versions but not all

/** User's resolution for a conflict. */
sealed trait Resolution

object Resolution:
  case class UseType(typeExpr: TypeExpr) extends Resolution
  case class DualAccessor(types: Map[String, TypeExpr]) extends Resolution
  case class Custom(decls: Vector[Decl]) extends Resolution
  case object Skip extends Resolution

/** User-provided strategy for resolving conflicts. */
trait MergeStrategy[F[_]]:
  def onConflict(conflict: Conflict): Pipe[F, Resolution]

object Merge:

  /** Well-known Meta keys set by merge. */
  object Keys:
    val presentIn: Meta.Key[Vector[String]]       = Meta.Key("merge.presentIn")
    val conflictType: Meta.Key[String]             = Meta.Key("merge.conflictType")
    val typePerVersion: Meta.Key[Map[String, TypeExpr]] = Meta.Key("merge.typePerVersion")
    val sources: Meta.Key[Vector[String]]          = Meta.Key("merge.sources")

  /** Merge N versioned modules into one. */
  def merge[F[_]: Monad](
    versions: NonEmptyVector[(String, Module)],
    strategy: MergeStrategy[F]
  ): Pipe[F, Module] =
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
  ): Pipe[F, CompilationUnit] =
    val allDecls = versionedUnits.flatMap { (v, u) =>
      u.declarations.map(d => (v, d))
    }
    val byName = allDecls.groupBy((_, d) => declName(d))

    byName.toVector.traverse { (name, vDecls) =>
      mergeDecl(name, vDecls, allVersions, strategy)
    }.map(decls => CompilationUnit(namespace, decls.flatten))

  private def mergeDecl[F[_]: Monad](
    name: String,
    versionedDecls: Vector[(String, Decl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Pipe[F, Option[Decl]] =
    versionedDecls match
      case Vector((v, single)) =>
        // present in only one version
        val meta = single match
          case td: Decl.TypeDecl => td.meta.set(Keys.presentIn, Vector(v))
          case _                 => Meta.empty.set(Keys.presentIn, Vector(v))
        Pipe.pure(Some(withMeta(single, meta)))

      case multiple =>
        val presentVersions = multiple.map(_._1)
        val decls = multiple.map(_._2)

        // all same kind?
        val allTypeDecls = decls.collect { case td: Decl.TypeDecl => td }
        val allEnumDecls = decls.collect { case ed: Decl.EnumDecl => ed }

        if allTypeDecls.size == decls.size then
          mergeTypeDecls(name, multiple.map((v, d) => (v, d.asInstanceOf[Decl.TypeDecl])), allVersions, strategy)
            .map(Some(_))
        else if allEnumDecls.size == decls.size then
          Pipe.pure(Some(mergeEnumDecls(name, multiple.map((v, d) => (v, d.asInstanceOf[Decl.EnumDecl])), presentVersions)))
        else
          // mixed kinds -- take first, warn
          Pipe.warn[F](s"Mixed declaration kinds for '$name', using first").as(Some(decls.head))

  private def mergeTypeDecls[F[_]: Monad](
    name: String,
    versioned: Vector[(String, Decl.TypeDecl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Pipe[F, Decl] =
    val presentVersions = versioned.map(_._1)
    val first = versioned.head._2

    // merge functions
    val allFuncs = versioned.flatMap { (v, td) =>
      td.functions.map(f => (v, f))
    }
    val funcsByName = allFuncs.groupBy(_._2.name)

    funcsByName.toVector.traverse { (fname, vFuncs) =>
      mergeFunctions(name, fname, vFuncs, presentVersions, strategy)
    }.map { mergedFuncs =>
      // merge fields (union by name, first type wins)
      val allFields = versioned.flatMap(_._2.fields)
      val mergedFields = allFields.distinctBy(_.name)

      // merge nested
      val allNested = versioned.flatMap(_._2.nested)
      val mergedNested = allNested.distinctBy(declName)

      // merge supertypes
      val mergedSupertypes = versioned.flatMap(_._2.supertypes).distinct

      val meta = first.meta
        .set(Keys.presentIn, presentVersions)
        .set(Keys.sources, allVersions)

      Decl.TypeDecl(
        name = name,
        kind = first.kind,
        fields = mergedFields,
        functions = mergedFuncs.flatten,
        nested = mergedNested,
        supertypes = mergedSupertypes,
        typeParams = first.typeParams,
        visibility = first.visibility,
        annotations = first.annotations,
        meta = meta
      )
    }

  @scala.annotation.nowarn("msg=unused explicit parameter")
  private def mergeFunctions[F[_]: Monad](
    declName: String,
    funcName: String,
    versioned: Vector[(String, Func)],
    allPresentVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): Pipe[F, Option[Func]] =
    val presentIn = versioned.map(_._1)
    val first = versioned.head._2

    // check return type conflict
    val returnTypes = versioned.map((v, f) => (v, f.returnType)).distinctBy(_._2)

    if returnTypes.size <= 1 then
      // no conflict -- annotate with presentIn
      val meta = first.meta.set(Keys.presentIn, presentIn)
      Pipe.pure(Some(first.copy(meta = meta)))
    else
      // conflict detected
      val conflict = Conflict(
        declName = declName,
        memberName = funcName,
        kind = ConflictKind.FuncReturnType,
        versions = NonEmptyVector.fromVectorUnsafe(versioned.map((v, f) => (v, f.returnType))),
        meta = Meta.empty
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
          // custom decls handled at higher level
          val meta = first.meta.set(Keys.presentIn, presentIn)
          Some(first.copy(meta = meta))
        case Resolution.Skip =>
          None
      }

  @scala.annotation.nowarn("msg=unused explicit parameter")
  private def mergeEnumDecls(
    name: String,
    versioned: Vector[(String, Decl.EnumDecl)],
    presentVersions: Vector[String]
  ): Decl =
    val first = versioned.head._2
    val allVariants = versioned.flatMap(_._2.variants).distinctBy(_.name)
    val meta = first.meta.set(Keys.presentIn, presentVersions)
    first.copy(variants = allVariants, meta = meta)

  // -- Helpers --

  private def declName(d: Decl): String = d match
    case Decl.TypeDecl(n, _, _, _, _, _, _, _, _, _) => n
    case Decl.EnumDecl(n, _, _, _, _, _, _)          => n
    case Decl.FuncDecl(f, _)                         => f.name
    case Decl.AliasDecl(n, _, _, _)                  => n
    case Decl.ConstDecl(n, _, _, _, _)               => n

  private def withMeta(d: Decl, meta: Meta): Decl = d match
    case td: Decl.TypeDecl  => td.copy(meta = meta)
    case ed: Decl.EnumDecl  => ed.copy(meta = meta)
    case fd: Decl.FuncDecl  => fd.copy(meta = meta)
    case ad: Decl.AliasDecl => ad.copy(meta = meta)
    case cd: Decl.ConstDecl => cd.copy(meta = meta)
