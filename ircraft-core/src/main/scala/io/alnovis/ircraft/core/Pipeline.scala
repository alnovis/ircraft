package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

object Pipeline:

  /** Compose passes left to right. Fail-fast is automatic via MonadThrow. */
  def of[F[_]: FlatMap](first: Pass[F], rest: Pass[F]*): Pass[F] =
    rest.foldLeft(first)(_ andThen _)

  /** Compose of a vector, skipping disabled passes. */
  def build[F[_]: Monad](passes: Vector[(Pass[F], Boolean)]): Pass[F] =
    val enabled = passes.collect { case (p, true) => p }
    if enabled.isEmpty then Pass.id[F]
    else enabled.reduceLeft(_ andThen _)

  /** Run a pipeline on a module. */
  def run[F[_]](pipeline: Pass[F], module: Module): F[Module] =
    pipeline.run(module)
