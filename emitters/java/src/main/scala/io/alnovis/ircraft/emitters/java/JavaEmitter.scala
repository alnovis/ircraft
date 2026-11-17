package io.alnovis.ircraft.emitters.java

import cats._
import io.alnovis.ircraft.emit.{ BaseEmitter, LanguageSyntax, TypeMapping }

/**
  * Code emitter that produces Java source files from the semantic IR.
  *
  * Extends [[BaseEmitter]] with Java-specific syntax rules ([[JavaSyntax]]) and
  * type mappings ([[JavaTypeMapping]]). Supports all Java constructs including
  * classes, interfaces, enums, fields, methods, and control flow.
  *
  * Pattern matching in the IR is desugared to if/else chains since Java does not
  * have native pattern matching (pre-21).
  *
  * @tparam F the effect type, constrained to `Monad`
  *
  * @example {{{
  * val emitter = JavaEmitter[cats.Id]
  * val files: Map[java.nio.file.Path, String] = emitter(myModule)
  * }}}
  *
  * @see [[JavaSyntax]] for Java syntax rules
  * @see [[JavaTypeMapping]] for Java type name resolution
  * @see [[BaseEmitter]] for the base emitter API
  */
class JavaEmitter[F[_]: Monad](collisionSet: Set[String] = Set.empty) extends BaseEmitter[F] {
  protected val syntax: LanguageSyntax = JavaSyntax
  protected val tm: TypeMapping =
    if (collisionSet.isEmpty) JavaTypeMapping
    else JavaTypeMapping.withCollisions(collisionSet)

  override protected def withCollisions(collisions: Set[String]): JavaEmitter[F] =
    new JavaEmitter[F](collisions)
}

/**
  * Factory companion for [[JavaEmitter]].
  */
object JavaEmitter {

  /**
    * Creates a new [[JavaEmitter]] instance.
    *
    * @tparam F the effect type, constrained to `Monad`
    * @return a new Java emitter
    */
  def apply[F[_]: Monad]: JavaEmitter[F] = new JavaEmitter[F]
}
