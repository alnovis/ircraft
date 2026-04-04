package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

/** Lowering = Kleisli from user's source dialect to Module in F. */
type Lowering[F[_], Source] = Kleisli[F, Source, Module]

object Lowering:

  /** Create a lowering from a pure function. */
  def pure[F[_]: Applicative, Source](f: Source => Module): Lowering[F, Source] =
    Kleisli(source => Applicative[F].pure(f(source)))

  /** Create a lowering with effects. */
  def apply[F[_], Source](f: Source => F[Module]): Lowering[F, Source] =
    Kleisli(f)
