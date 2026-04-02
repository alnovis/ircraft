package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

object Pipeline:

  /** Compose passes left to right. */
  def of[F[_]: Monad](first: Pass[F], rest: Pass[F]*): Pass[F] =
    rest.foldLeft(first)(_ andThen _)

  /** Compose from a vector, skipping disabled passes. */
  def build[F[_]: Monad](passes: Vector[(Pass[F], Boolean)]): Pass[F] =
    val enabled = passes.collect { case (p, true) => p }
    if enabled.isEmpty then Pass.id[F]
    else enabled.reduceLeft(_ andThen _)

  /** Run a pipeline on a module, returning (diagnostics, result). */
  def run[F[_]: Monad](pipeline: Pass[F], module: Module): F[(Chain[Diagnostic], Module)] =
    pipeline.run(module).run
