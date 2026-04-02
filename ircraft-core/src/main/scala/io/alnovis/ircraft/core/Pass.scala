package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

/** Pass = Kleisli[F, Module, Module]. Composable via andThen. */
type Pass[F[_]] = Kleisli[F, Module, Module]

object Pass:

  /** Create a pass from an effectful function. */
  def apply[F[_]](name: String)(f: Module => F[Module]): Pass[F] =
    Kleisli(f)

  /** Create a pure pass (no effects). */
  def pure[F[_]: Applicative](name: String)(f: Module => Module): Pass[F] =
    Kleisli(m => Applicative[F].pure(f(m)))

  /** Identity pass -- returns module unchanged. */
  def id[F[_]: Applicative]: Pass[F] =
    Kleisli(m => Applicative[F].pure(m))
