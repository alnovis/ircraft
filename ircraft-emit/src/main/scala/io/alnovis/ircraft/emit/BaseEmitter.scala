package io.alnovis.ircraft.emit

import cats.*
import cats.data.*
import cats.syntax.all.*
import java.nio.file.Path
import scala.collection.immutable.SortedMap
import io.alnovis.ircraft.core.ir.*

/** Emitter = Kleisli[F, Module, Map[Path, String]]. Composable with Pass and Lowering. */
type Emitter[F[_]] = Kleisli[F, Module, Map[Path, String]]

/**
 * Two-phase emitter:
 *   Phase 1: Semantic IR -> CodeNode tree (effectful, via F)
 *   Phase 2: CodeNode -> String (pure, via Renderer)
 *
 * Subclasses implement Phase 1 hooks. Phase 2 is automatic.
 */
abstract class BaseEmitter[F[_]: Monad]:

  protected def tm: TypeMapping
  protected def fileExtension: String
  protected def statementTerminator: String

  // -- Phase 1: IR -> CodeNode (effectful) --
  protected def emitDeclTree(decl: Decl): F[CodeNode]
  protected def emitExprText(expr: Expr): String

  /** Kleisli emitter for composition with Pass and Lowering. */
  final val emitter: Emitter[F] = Kleisli(apply)

  final def apply(module: Module): F[Map[Path, String]] =
    module.units.flatTraverse { unit =>
      unit.declarations.traverse { decl =>
        emitFileTree(unit.namespace, decl).map { tree =>
          val name = declName(decl)
          val path = Path.of(unit.namespace.replace('.', '/'), s"$name.$fileExtension")
          val source = Renderer.render(tree, statementTerminator)
          (path, source)
        }
      }
    }.map { pairs =>
      val filtered = pairs.filter((_, source) => source.trim.nonEmpty)
      SortedMap.from(filtered)(using Ordering.by(_.toString))
    }

  /** Build CodeNode tree for a file (public for testing at tree level). */
  def toFileTree(namespace: String, decl: Decl): F[CodeNode] =
    emitFileTree(namespace, decl)

  /** Build CodeNode tree for a declaration (public for testing at tree level). */
  def toDeclTree(decl: Decl): F[CodeNode] =
    emitDeclTree(decl)

  protected def emitFileTree(namespace: String, decl: Decl): F[CodeNode] =
    emitDeclTree(decl).map { declTree =>
      val imports = ImportCollector.collect(decl, tm)
      CodeNode.File(s"package $namespace", imports.toVector, Vector(declTree))
    }

  protected def declName(decl: Decl): String = decl.name
