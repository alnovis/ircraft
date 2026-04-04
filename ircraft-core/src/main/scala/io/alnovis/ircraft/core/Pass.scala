package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.{ Meta, Module }

/** Pass = Kleisli[F, Module, Module]. Composable via andThen. */
type Pass[F[_]] = Kleisli[F, Module, Module]

object Pass:

  /** Meta key for the pass trace — records names of all passes that have run. */
  val traceKey: Meta.Key[Chain[String]] = Meta.Key("pass.trace")

  /** Create a pass from an effectful function. Appends name to trace. */
  def apply[F[_]: Functor](name: String)(f: Module => F[Module]): Pass[F] =
    Kleisli { m =>
      f(m).map(appendTrace(_, name))
    }

  /** Create a pure pass (no effects). Appends name to trace. */
  def pure[F[_]: Applicative](name: String)(f: Module => Module): Pass[F] =
    Kleisli { m =>
      Applicative[F].pure(appendTrace(f(m), name))
    }

  /** Identity pass -- returns module unchanged. */
  def id[F[_]: Applicative]: Pass[F] =
    Kleisli(m => Applicative[F].pure(m))

  private def appendTrace(module: Module, name: String): Module =
    val trace = module.meta.get(traceKey).getOrElse(Chain.empty)
    module.copy(meta = module.meta.set(traceKey, trace :+ name))
