package io.alnovis.ircraft.core

import cats._
import cats.data._
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{Meta, Module, SemanticF}

// Pass type alias defined in package.scala: Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]

object Pass {

  val traceKey: Meta.Key[Chain[String]] = Meta.Key("pass.trace")

  def apply[F[_]: Functor](name: String)(f: Module[Fix[SemanticF]] => F[Module[Fix[SemanticF]]]): Pass[F] =
    Kleisli { m =>
      f(m).map(appendTrace(_, name))
    }

  def pure[F[_]: Applicative](name: String)(f: Module[Fix[SemanticF]] => Module[Fix[SemanticF]]): Pass[F] =
    Kleisli { m =>
      Applicative[F].pure(appendTrace(f(m), name))
    }

  def id[F[_]: Applicative]: Pass[F] =
    Kleisli(m => Applicative[F].pure(m))

  private def appendTrace(module: Module[Fix[SemanticF]], name: String): Module[Fix[SemanticF]] = {
    val trace = module.meta.get(traceKey).getOrElse(Chain.empty)
    module.copy(meta = module.meta.set(traceKey, trace :+ name))
  }
}
