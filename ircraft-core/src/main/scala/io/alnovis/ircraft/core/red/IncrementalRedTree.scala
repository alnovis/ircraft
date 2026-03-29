package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.{ Module, Operation }

/**
  * Incremental Red Tree: compare old and new green trees, identify changes.
  *
  * Uses contentHash for O(1) per-node change detection -- unchanged subtrees are skipped entirely.
  */
object IncrementalRedTree:

  case class DiffEntry(path: Vector[Int], kind: DiffKind)

  enum DiffKind:
    case Added, Removed, Modified

  /** Rebuild Red Tree from a new module. */
  def rebuild(oldRoot: RedNode[Module], newModule: Module): RedNode[Module] =
    RedTree.from(newModule)

  /** Compare old and new modules, returning paths to changed operations. */
  def diff(oldModule: Module, newModule: Module): Vector[DiffEntry] =
    if oldModule.contentHash == newModule.contentHash then Vector.empty
    else diffChildren(oldModule.topLevel, newModule.topLevel, Vector.empty)

  private def diffChildren(
    oldOps: Vector[Operation],
    newOps: Vector[Operation],
    pathPrefix: Vector[Int]
  ): Vector[DiffEntry] =
    val maxLen = math.max(oldOps.size, newOps.size)
    (0 until maxLen).toVector.flatMap { idx =>
      val path = pathPrefix :+ idx
      (oldOps.lift(idx), newOps.lift(idx)) match
        case (None, Some(_)) => Vector(DiffEntry(path, DiffKind.Added))
        case (Some(_), None) => Vector(DiffEntry(path, DiffKind.Removed))
        case (Some(o), Some(n)) =>
          if o.contentHash == n.contentHash then Vector.empty
          else
            Vector(DiffEntry(path, DiffKind.Modified)) ++
              diffChildren(o.children, n.children, path)
        case _ => Vector.empty
    }
