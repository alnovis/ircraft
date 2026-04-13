package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{ Field, Func, Meta, Visibility }

/**
  * Structural typeclasses for dialect functors.
  *
  * These traits allow generic passes that work across ANY dialect
  * without knowing its concrete type. Each trait extracts a specific
  * structural aspect from a dialect operation.
  */

trait HasName[F[_]] {
  def name[A](fa: F[A]): String
}

object HasName {
  def apply[F[_]](implicit ev: HasName[F]): HasName[F] = ev
}

trait HasMeta[F[_]] {
  def meta[A](fa: F[A]): Meta
  def withMeta[A](fa: F[A], m: Meta): F[A]
}

object HasMeta {
  def apply[F[_]](implicit ev: HasMeta[F]): HasMeta[F] = ev
}

trait HasFields[F[_]] {
  def fields[A](fa: F[A]): Vector[Field]
}

object HasFields {
  def apply[F[_]](implicit ev: HasFields[F]): HasFields[F] = ev
}

trait HasMethods[F[_]] {
  def functions[A](fa: F[A]): Vector[Func]
}

object HasMethods {
  def apply[F[_]](implicit ev: HasMethods[F]): HasMethods[F] = ev
}

trait HasNested[F[_]] {
  def nested[A](fa: F[A]): Vector[A]
}

object HasNested {
  def apply[F[_]](implicit ev: HasNested[F]): HasNested[F] = ev
}

trait HasVisibility[F[_]] {
  def visibility[A](fa: F[A]): Visibility
}

object HasVisibility {
  def apply[F[_]](implicit ev: HasVisibility[F]): HasVisibility[F] = ev
}
