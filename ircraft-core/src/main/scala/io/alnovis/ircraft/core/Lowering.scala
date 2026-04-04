package io.alnovis.ircraft.core

import cats._
import cats.data._
import io.alnovis.ircraft.core.ir.Module

// Lowering type alias defined in package.scala

object Lowering {

  def pure[F[_]: Applicative, Source](f: Source => Module): Lowering[F, Source] =
    Kleisli(source => Applicative[F].pure(f(source)))

  def apply[F[_], Source](f: Source => F[Module]): Lowering[F, Source] =
    Kleisli(f)
}
