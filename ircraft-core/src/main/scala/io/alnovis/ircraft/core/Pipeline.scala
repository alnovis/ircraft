package io.alnovis.ircraft.core

import cats._
import cats.data._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir.{ Module, SemanticF }

object Pipeline {

  def of[F[_]: FlatMap](first: Pass[F], rest: Pass[F]*): Pass[F] =
    rest.foldLeft(first)(_ andThen _)

  def build[F[_]: Monad](passes: Vector[(Pass[F], Boolean)]): Pass[F] = {
    val enabled = passes.collect { case (p, true) => p }
    if (enabled.isEmpty) Pass.id[F]
    else enabled.reduceLeft(_ andThen _)
  }

  def run[F[_]](pipeline: Pass[F], module: Module[Fix[SemanticF]]): F[Module[Fix[SemanticF]]] =
    pipeline.run(module)
}
