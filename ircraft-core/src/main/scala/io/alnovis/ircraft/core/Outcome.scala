package io.alnovis.ircraft.core

import cats.*
import cats.data.*

/**
  * Unified error/warning channel for ircraft pipelines.
  *
  * Three states:
  *   - Right(a)        -- clean success
  *   - Both(diags, a)  -- success with warnings (pipeline continues)
  *   - Left(diags)     -- error (pipeline stops)
  */
type Outcome[F[_], A] = IorT[F, NonEmptyChain[Diagnostic], A]

object Outcome:

  def ok[F[_]: Applicative, A](a: A): Outcome[F, A] =
    IorT.pure(a)

  def warn[F[_]: Applicative, A](msg: String, a: A, location: Option[String] = None): Outcome[F, A] =
    IorT.fromIor(Ior.Both(NonEmptyChain.one(Diagnostic(Severity.Warning, msg, location)), a))

  def fail[F[_]: Applicative, A](msg: String, location: Option[String] = None): Outcome[F, A] =
    IorT.fromIor(Ior.Left(NonEmptyChain.one(Diagnostic(Severity.Error, msg, location))))

  def failNec[F[_]: Applicative, A](diags: NonEmptyChain[Diagnostic]): Outcome[F, A] =
    IorT.fromIor(Ior.Left(diags))

  def warnAll[F[_]: Applicative, A](diags: NonEmptyChain[Diagnostic], a: A): Outcome[F, A] =
    IorT.fromIor(Ior.Both(diags, a))

  def liftF[F[_]: Applicative, A](fa: F[A]): Outcome[F, A] =
    IorT.liftF(fa)

  def fromIor[F[_]: Applicative, A](ior: Ior[NonEmptyChain[Diagnostic], A]): Outcome[F, A] =
    IorT.fromIor(ior)
