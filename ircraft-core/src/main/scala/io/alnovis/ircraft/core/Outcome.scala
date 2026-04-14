package io.alnovis.ircraft.core

import cats._
import cats.data._

/**
  * Smart constructors for `IorT`-based error/warning handling.
  *
  * [[Outcome]] wraps `IorT[F, NonEmptyChain[Diagnostic], A]` and provides
  * ergonomic factory methods for the three possible states:
  *
  *  - '''Success''' (`Ior.Right`): a pure value with no diagnostics.
  *  - '''Warning''' (`Ior.Both`): a value accompanied by one or more non-fatal diagnostics.
  *  - '''Failure''' (`Ior.Left`): one or more error diagnostics with no value.
  *
  * On Scala 3, a type alias `Outcome[F, A]` is available in the `core` package object.
  * On Scala 2, use `IorT[F, NonEmptyChain[Diagnostic], A]` directly.
  *
  * {{{
  * import cats.Id
  *
  * val success = Outcome.ok[Id, Int](42)
  * val warned  = Outcome.warn[Id, Int]("minor issue", 42)
  * val failed  = Outcome.fail[Id, Int]("fatal error")
  * }}}
  *
  * @see [[Diagnostic]] for the diagnostic type accumulated on the left side
  * @see [[Severity]] for severity levels within diagnostics
  */
object Outcome {

  private type Diags = NonEmptyChain[Diagnostic]

  /**
    * Creates a successful outcome containing only a value, with no diagnostics.
    *
    * @tparam F the effect type
    * @tparam A the value type
    * @param a the success value
    * @return an `IorT` containing `Ior.Right(a)`
    */
  def ok[F[_]: Applicative, A](a: A): IorT[F, Diags, A] =
    IorT.pure(a)

  /**
    * Creates an outcome that carries both a warning diagnostic and a value.
    *
    * The resulting `IorT` is in the `Ior.Both` state, meaning downstream
    * consumers receive the value but should inspect accumulated warnings.
    *
    * @tparam F the effect type
    * @tparam A the value type
    * @param msg      the warning message text
    * @param a        the value to carry alongside the warning
    * @param location an optional location identifier for the warning
    * @return an `IorT` containing `Ior.Both(warnings, a)`
    */
  def warn[F[_]: Applicative, A](msg: String, a: A, location: Option[String] = None): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Both(NonEmptyChain.one(Diagnostic(Severity.Warning, msg, location)), a))

  /**
    * Creates a failed outcome with a single error diagnostic and no value.
    *
    * The resulting `IorT` is in the `Ior.Left` state, which short-circuits
    * further `flatMap` composition.
    *
    * @tparam F the effect type
    * @tparam A the (phantom) value type
    * @param msg      the error message text
    * @param location an optional location identifier for the error
    * @return an `IorT` containing `Ior.Left(errors)`
    */
  def fail[F[_]: Applicative, A](msg: String, location: Option[String] = None): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Left(NonEmptyChain.one(Diagnostic(Severity.Error, msg, location))))

  /**
    * Creates a failed outcome from a pre-built chain of diagnostics.
    *
    * Useful when multiple errors need to be reported at once.
    *
    * @tparam F the effect type
    * @tparam A the (phantom) value type
    * @param diags a non-empty chain of diagnostics (typically errors)
    * @return an `IorT` containing `Ior.Left(diags)`
    */
  def failNec[F[_]: Applicative, A](diags: Diags): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Left(diags))

  /**
    * Creates an outcome that carries multiple warning diagnostics alongside a value.
    *
    * @tparam F the effect type
    * @tparam A the value type
    * @param diags a non-empty chain of warning diagnostics
    * @param a     the value to carry alongside the warnings
    * @return an `IorT` containing `Ior.Both(diags, a)`
    */
  def warnAll[F[_]: Applicative, A](diags: Diags, a: A): IorT[F, Diags, A] =
    IorT.fromIor(Ior.Both(diags, a))

  /**
    * Lifts an effectful value into an [[Outcome]] with no diagnostics.
    *
    * @tparam F the effect type
    * @tparam A the value type
    * @param fa the effectful value to lift
    * @return an `IorT` wrapping `fa` in the success channel
    */
  def liftF[F[_]: Applicative, A](fa: F[A]): IorT[F, Diags, A] =
    IorT.liftF(fa)

  /**
    * Lifts a plain `Ior` into an effectful `IorT`.
    *
    * @tparam F the effect type
    * @tparam A the value type
    * @param ior the `Ior` value to lift
    * @return an `IorT` wrapping `ior` inside `F`
    */
  def fromIor[F[_]: Applicative, A](ior: Ior[Diags, A]): IorT[F, Diags, A] =
    IorT.fromIor(ior)
}
