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
  def onConflict(conflict: Conflict): F[Resolution]

object Merge:

  /** Well-known Meta keys set by merge. */
  object Keys:
    val presentIn: Meta.Key[Vector[String]]       = Meta.Key("merge.presentIn")
    val conflictType: Meta.Key[String]             = Meta.Key("merge.conflictType")
    val typePerVersion: Meta.Key[Map[String, TypeExpr]] = Meta.Key("merge.typePerVersion")
    val sources: Meta.Key[Vector[String]]          = Meta.Key("merge.sources")

  /** Merge N versioned modules into one. Returns diagnostics + merged module. */
  def merge[F[_]: Monad](
    versions: NonEmptyVector[(String, Module)],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], Module)] =
    val versionNames = versions.map(_._1).toVector
    val allUnits = versions.toVector.flatMap { (vn, m) =>
      m.units.map(u => (vn, u))
    }
    val byNamespace = allUnits.groupBy(_._2.namespace)

    traverseAccum(byNamespace.toVector) { (ns, vUnits) =>
      mergeUnits(ns, vUnits, versionNames, strategy)
    }.map { (diags, units) =>
      (diags, Module(versionNames.mkString("+"), units, Meta.empty.set(Keys.sources, versionNames)))
    }

  private def mergeUnits[F[_]: Monad](
    namespace: String,
    versionedUnits: Vector[(String, CompilationUnit)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], CompilationUnit)] =
    val allDecls = versionedUnits.flatMap { (v, u) =>
      u.declarations.map(d => (v, d))
    }
    val byName = allDecls.groupBy((_, d) => d.name)

    traverseAccum(byName.toVector) { (name, vDecls) =>
      mergeDecl(name, vDecls, allVersions, strategy)
    }.map { (diags, decls) =>
      (diags, CompilationUnit(namespace, decls.flatten))
    }

  private def mergeDecl[F[_]: Monad](
    name: String,
    versionedDecls: Vector[(String, Decl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], Option[Decl])] =
    versionedDecls match
      case Vector((v, single)) =>
        // present in only one version
        val meta = single match
          case td: Decl.TypeDecl => td.meta.set(Keys.presentIn, Vector(v))
          case _                 => Meta.empty.set(Keys.presentIn, Vector(v))
        Monad[F].pure((Vector.empty, Some(single.withMeta(meta))))

      case multiple =>
        val presentVersions = multiple.map(_._1)
        val decls = multiple.map(_._2)

        // all same kind?
        val allTypeDecls = decls.collect { case td: Decl.TypeDecl => td }
        val allEnumDecls = decls.collect { case ed: Decl.EnumDecl => ed }

        if allTypeDecls.size == decls.size then
          val typed = multiple.collect { case (v, td: Decl.TypeDecl) => (v, td) }
          mergeTypeDecls(name, typed, allVersions, strategy).map((d, r) => (d, Some(r)))
        else if allEnumDecls.size == decls.size then
          val typed = multiple.collect { case (v, ed: Decl.EnumDecl) => (v, ed) }
          Monad[F].pure((Vector.empty, Some(mergeEnumDecls(name, typed, presentVersions))))
        else
          // mixed kinds -- take first, warn
          val diag = Diagnostic(Severity.Warning, s"Mixed declaration kinds for '$name', using first")
          Monad[F].pure((Vector(diag), Some(decls.head)))

  private def mergeTypeDecls[F[_]: Monad](
    name: String,
    versioned: Vector[(String, Decl.TypeDecl)],
    allVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], Decl)] =
    val presentVersions = versioned.map(_._1)
    val first = versioned.head._2

    // merge functions
    val allFuncs = versioned.flatMap { (v, td) =>
      td.functions.map(f => (v, f))
    }
    val funcsByName = allFuncs.groupBy(_._2.name)

    // merge fields with conflict detection
    val allFieldEntries = versioned.flatMap { (v, td) => td.fields.map(f => (v, f)) }
    val fieldsByName = allFieldEntries.groupBy(_._2.name)

    // merge nested — recursive, with conflict detection
    val allNestedEntries = versioned.flatMap { (v, td) => td.nested.map(d => (v, d)) }
    val nestedByName = allNestedEntries.groupBy((_, d) => d.name)

    for
      funcsResult  <- traverseAccum(funcsByName.toVector) { (fname, vFuncs) =>
        mergeFunctions(name, fname, vFuncs, presentVersions, strategy)
      }
      fieldsResult <- traverseAccum(fieldsByName.toVector) { (fname, vFields) =>
        mergeFields(name, fname, vFields, strategy)
      }
      nestedResult <- traverseAccum(nestedByName.toVector) { (nname, vNested) =>
        mergeDecl(nname, vNested, allVersions, strategy)
      }
    yield
      val (funcDiags, mergedFuncs) = funcsResult
      val (fieldDiags, mergedFields) = fieldsResult
      val (nestedDiags, mergedNested) = nestedResult

      // merge supertypes
      val mergedSupertypes = versioned.flatMap(_._2.supertypes).distinct

      val meta = first.meta
        .set(Keys.presentIn, presentVersions)
        .set(Keys.sources, allVersions)

      val decl = Decl.TypeDecl(
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
      (funcDiags ++ fieldDiags ++ nestedDiags, decl)

  private def mergeFields[F[_]: Monad](
    declName: String,
    fieldName: String,
    versioned: Vector[(String, Field)],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], Option[Field])] =
    val first = versioned.head._2
    val types = versioned.map((v, f) => (v, f.fieldType)).distinctBy(_._2)
    if types.size <= 1 then
      Monad[F].pure((Vector.empty, Some(first)))
    else
      val conflict = Conflict(
        declName = declName,
        memberName = fieldName,
        kind = ConflictKind.FieldType,
        versions = NonEmptyVector.fromVectorUnsafe(versioned.map((v, f) => (v, f.fieldType))),
        meta = Meta.empty
      )
      strategy.onConflict(conflict).map {
        case Resolution.UseType(t) => (Vector.empty, Some(first.copy(fieldType = t)))
        case Resolution.Skip       => (Vector.empty, None)
        case _                     => (Vector.empty, Some(first))
      }

  @scala.annotation.nowarn("msg=unused explicit parameter")
  private def mergeFunctions[F[_]: Monad](
    declName: String,
    funcName: String,
    versioned: Vector[(String, Func)],
    allPresentVersions: Vector[String],
    strategy: MergeStrategy[F]
  ): F[(Vector[Diagnostic], Option[Func])] =
    val presentIn = versioned.map(_._1)
    val first = versioned.head._2

    // check return type conflict
    val returnTypes = versioned.map((v, f) => (v, f.returnType)).distinctBy(_._2)

    if returnTypes.size <= 1 then
      // no conflict -- annotate with presentIn
      val meta = first.meta.set(Keys.presentIn, presentIn)
      Monad[F].pure((Vector.empty, Some(first.copy(meta = meta))))
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
          (Vector.empty, Some(first.copy(returnType = t, meta = meta)))
        case Resolution.DualAccessor(types) =>
          val meta = first.meta
            .set(Keys.presentIn, presentIn)
            .set(Keys.conflictType, "DUAL_ACCESSOR")
            .set(Keys.typePerVersion, types)
          (Vector.empty, Some(first.copy(meta = meta)))
        case Resolution.Custom(_) =>
          // custom decls handled at higher level
          val meta = first.meta.set(Keys.presentIn, presentIn)
          (Vector.empty, Some(first.copy(meta = meta)))
        case Resolution.Skip =>
          (Vector.empty, None)
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

  /** Traverse a collection, accumulating diagnostics alongside results. */
  private def traverseAccum[F[_]: Monad, A, B](
    items: Vector[A]
  )(f: A => F[(Vector[Diagnostic], B)]): F[(Vector[Diagnostic], Vector[B])] =
    items.foldLeftM((Vector.empty[Diagnostic], Vector.empty[B])) { case ((accDiags, accResults), item) =>
      f(item).map { (diags, result) => (accDiags ++ diags, accResults :+ result) }
    }
