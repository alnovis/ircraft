package io.alnovis.ircraft.emitters.java

import cats._
import io.alnovis.ircraft.emit.{ BaseEmitter, LanguageSyntax, TypeMapping }

class JavaEmitter[F[_]: Monad] extends BaseEmitter[F] {
  protected val syntax: LanguageSyntax = JavaSyntax
  protected val tm: TypeMapping        = JavaTypeMapping
}

object JavaEmitter {
  def apply[F[_]: Monad]: JavaEmitter[F] = new JavaEmitter[F]
}
