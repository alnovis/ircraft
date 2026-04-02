package io.alnovis.ircraft.core

import cats.*
import io.alnovis.ircraft.core.ir.Module

/** Lowering = function from user's source dialect to Module in F. */
type Lowering[F[_], Source] = Source => F[Module]

object Lowering:

  /** Create a lowering from a pure function. */
  def pure[F[_]: Applicative, Source](f: Source => Module): Lowering[F, Source] =
    source => Applicative[F].pure(f(source))

  /** Create a lowering with effects. */
  def apply[F[_], Source](f: Source => F[Module]): Lowering[F, Source] = f
