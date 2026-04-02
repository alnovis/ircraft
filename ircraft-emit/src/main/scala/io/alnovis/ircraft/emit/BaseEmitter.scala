package io.alnovis.ircraft.emit

import cats.*
import java.nio.file.Path
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*

/** Emitter = Module => Pipe[F, Map[Path, String]] */
type Emitter[F[_]] = Module => Pipe[F, Map[Path, String]]

/**
 * Two-phase emitter:
 *   Phase 1: Semantic IR -> CodeNode tree (effectful, via F)
 *   Phase 2: CodeNode -> String (pure, via Renderer)
 *
 * Subclasses implement Phase 1 hooks. Phase 2 is automatic.
 */
abstract class BaseEmitter[F[_]: Monad] extends (Module => Pipe[F, Map[Path, String]]):

  protected def tm: TypeMapping
  protected def fileExtension: String
  protected def statementTerminator: String

  // -- Phase 1: IR -> CodeNode (effectful) --
  protected def emitDeclTree(decl: Decl): Pipe[F, CodeNode]
  protected def emitExprText(expr: Expr): String

  final def apply(module: Module): Pipe[F, Map[Path, String]] =
    import cats.syntax.all.*
    module.units.flatTraverse { unit =>
      unit.declarations.traverse { decl =>
        emitFileTree(unit.namespace, decl).map { tree =>
          val name = declName(decl)
          val path = Path.of(unit.namespace.replace('.', '/'), s"$name.$fileExtension")
          val source = Renderer.render(tree, statementTerminator)
          path -> source
        }
      }
    }.map(_.toMap)

  /** Build CodeNode tree for a file (public for testing at tree level). */
  def toFileTree(namespace: String, decl: Decl): Pipe[F, CodeNode] =
    emitFileTree(namespace, decl)

  /** Build CodeNode tree for a declaration (public for testing at tree level). */
  def toDeclTree(decl: Decl): Pipe[F, CodeNode] =
    emitDeclTree(decl)

  protected def emitFileTree(namespace: String, decl: Decl): Pipe[F, CodeNode] =
    emitDeclTree(decl).map { declTree =>
      val imports = ImportCollector.collect(decl, tm)
      CodeNode.File(s"package $namespace", imports.toVector, Vector(declTree))
    }

  protected def declName(decl: Decl): String = decl match
    case Decl.TypeDecl(name, _, _, _, _, _, _, _, _, _) => name
    case Decl.EnumDecl(name, _, _, _, _, _, _)          => name
    case Decl.FuncDecl(func, _)                         => func.name
    case Decl.AliasDecl(name, _, _, _)                  => name
    case Decl.ConstDecl(name, _, _, _, _)               => name
