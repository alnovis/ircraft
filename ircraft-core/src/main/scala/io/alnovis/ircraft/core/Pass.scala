package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.Module

/** Pass = Kleisli[Pipe[F, *], Module, Module]. Composable via andThen. */
type Pass[F[_]] = Kleisli[[A] =>> Pipe[F, A], Module, Module]

object Pass:

  /** Create a pass from an effectful function. */
  def apply[F[_]: Monad](name: String)(f: Module => Pipe[F, Module]): Pass[F] =
    Kleisli(f)

  /** Create a pure pass (no diagnostics, no effects). */
  def pure[F[_]: Monad](name: String)(f: Module => Module): Pass[F] =
    Kleisli(m => WriterT.value(f(m)))

  /** Create a pass that produces diagnostics. */
  def withDiag[F[_]: Monad](name: String)(f: Module => (Chain[Diagnostic], Module)): Pass[F] =
    Kleisli(m => WriterT(Monad[F].pure(f(m))))

  /** Identity pass -- returns module unchanged. */
  def id[F[_]: Monad]: Pass[F] =
    Kleisli(m => WriterT.value(m))
