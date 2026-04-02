package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

/** Lowering = function from user's source dialect to Module. */
type Lowering[F[_], Source] = Source => Pipe[F, Module]

object Lowering:

  /** Create a lowering from a pure function (no diagnostics). */
  def pure[F[_]: Monad, Source](f: Source => Module): Lowering[F, Source] =
    source => WriterT.value(f(source))

  /** Create a lowering with diagnostics. */
  def apply[F[_]: Monad, Source](f: Source => Pipe[F, Module]): Lowering[F, Source] = f
