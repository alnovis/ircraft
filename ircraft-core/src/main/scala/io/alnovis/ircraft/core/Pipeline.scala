package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

object Pipeline:

  /** Compose passes left to right. */
  def of[F[_]: Monad](first: Pass[F], rest: Pass[F]*): Pass[F] =
    rest.foldLeft(first)(failFastCompose)

  /** Compose from a vector, skipping disabled passes. */
  def build[F[_]: Monad](passes: Vector[(Pass[F], Boolean)]): Pass[F] =
    val enabled = passes.collect { case (p, true) => p }
    if enabled.isEmpty then Pass.id[F]
    else enabled.reduceLeft(failFastCompose)

  /** Run a pipeline on a module, returning (diagnostics, result). */
  def run[F[_]: Monad](pipeline: Pass[F], module: Module): F[(Chain[Diagnostic], Module)] =
    pipeline.run(module).run

  /** Compose two passes with fail-fast: if first pass produces Error diagnostics, skip second. */
  private def failFastCompose[F[_]: Monad](a: Pass[F], b: Pass[F]): Pass[F] =
    Kleisli { module =>
      WriterT { Monad[F].flatMap(a.run(module).run) { (diags, result) =>
        if diags.exists(_.isError) then
          Monad[F].pure((diags, result))
        else
          Monad[F].map(b.run(result).run) { (diags2, result2) =>
            (diags ++ diags2, result2)
          }
      }}
    }
