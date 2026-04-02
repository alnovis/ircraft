package io.alnovis.ircraft.core

import cats.*
import cats.data.*

/** Core effect type: accumulates diagnostics via WriterT over user's F. */
type Pipe[F[_], A] = WriterT[F, Chain[Diagnostic], A]

object Pipe:

  def pure[F[_]: Applicative, A](a: A): Pipe[F, A] =
    WriterT.value(a)

  def tell[F[_]: Applicative](d: Diagnostic): Pipe[F, Unit] =
    WriterT.tell(Chain.one(d))

  def warn[F[_]: Applicative](msg: String, location: Option[String] = None): Pipe[F, Unit] =
    tell(Diagnostic(Severity.Warning, msg, location))

  def error[F[_]: Applicative](msg: String, location: Option[String] = None): Pipe[F, Unit] =
    tell(Diagnostic(Severity.Error, msg, location))

  def info[F[_]: Applicative](msg: String, location: Option[String] = None): Pipe[F, Unit] =
    tell(Diagnostic(Severity.Info, msg, location))

  def liftF[F[_]: Applicative, A](fa: F[A]): Pipe[F, A] =
    WriterT.liftF(fa)

  def raiseWhen[F[_]: Applicative](cond: Boolean)(msg: String): Pipe[F, Unit] =
    if cond then error(msg) else pure(())

  /** Run and extract (diagnostics, result). */
  def run[F[_], A](pipe: Pipe[F, A]): F[(Chain[Diagnostic], A)] =
    pipe.run
