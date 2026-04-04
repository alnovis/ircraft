package io.alnovis.ircraft.core

import cats._
import cats.data._

/**
  * Smart constructors for IorT-based error/warning handling.
  *
  * On Scala 3, a type alias `Outcome[F, A]` is available.
  * On Scala 2, use `IorT[F, NonEmptyChain[Diagnostic], A]` directly.
  */
object Outcome {

  private type Diags = NonEmptyChain[Diagnostic]

  def ok[F[_]: Applicative, A](a: A): IorT[F, Diags, A] =
    IorT.pure(a)

  def warn[F[_]: Applicative, A](msg: String, a: A, location: Option[String] = None): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Both(NonEmptyChain.one(Diagnostic(Severity.Warning, msg, location)), a))

  def fail[F[_]: Applicative, A](msg: String, location: Option[String] = None): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Left(NonEmptyChain.one(Diagnostic(Severity.Error, msg, location))))

  def failNec[F[_]: Applicative, A](diags: Diags): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Left(diags))

  def warnAll[F[_]: Applicative, A](diags: Diags, a: A): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Both(diags, a))

  def liftF[F[_]: Applicative, A](fa: F[A]): IorT[F, Diags, A] =
    IorT.liftF(fa)

  def fromIor[F[_]: Applicative, A](ior: Ior[Diags, A]): IorT[F, Diags, A] =
    IorT.fromIor(ior)
}
