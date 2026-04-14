package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.{ Field, Func, Meta, Visibility }

/**
  * Structural typeclasses for dialect functors.
  *
  * These traits allow generic passes that work across ANY dialect
  * without knowing its concrete type. Each trait extracts a specific
  * structural aspect from a dialect operation.
  *
  * For example, a pass that renames all declarations can require only
  * `HasName[F]` and `HasMeta[F]` without depending on the concrete
  * dialect functor type.
  *
  * @see [[DialectInfo]] for dialect-level metadata (name, operation count)
  * @see [[ConstraintVerifier]] for generic verification using these traits
  */

/**
  * Typeclass for dialect functors whose operations have a name.
  *
  * Enables generic passes to extract the declaration name from any dialect
  * node without knowing the concrete functor type.
  *
  * @tparam F the dialect functor type
  * @see [[ConstraintVerifier.verifyNames]] for constraint verification using this typeclass
  */
trait HasName[F[_]] {

  /**
    * Extracts the name from a functor node.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the name of the declaration or operation
    */
  def name[A](fa: F[A]): String
}

/**
  * Companion object for [[HasName]], providing summoner.
  */
object HasName {

  /**
    * Summons the implicit [[HasName]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasName]] instance
    */
  def apply[F[_]](implicit ev: HasName[F]): HasName[F] = ev
}

/**
  * Typeclass for dialect functors whose operations carry [[Meta]] metadata.
  *
  * Enables generic passes to read and update metadata on any dialect node.
  *
  * @tparam F the dialect functor type
  */
trait HasMeta[F[_]] {

  /**
    * Extracts the metadata from a functor node.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the metadata attached to this node
    */
  def meta[A](fa: F[A]): Meta

  /**
    * Returns a copy of the functor node with the given metadata.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @param m  the new metadata to set
    * @return a copy of `fa` with its metadata replaced by `m`
    */
  def withMeta[A](fa: F[A], m: Meta): F[A]
}

/**
  * Companion object for [[HasMeta]], providing summoner.
  */
object HasMeta {

  /**
    * Summons the implicit [[HasMeta]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasMeta]] instance
    */
  def apply[F[_]](implicit ev: HasMeta[F]): HasMeta[F] = ev
}

/**
  * Typeclass for dialect functors whose operations contain [[Field]] definitions.
  *
  * Enables generic passes to inspect and validate field types across any dialect.
  *
  * @tparam F the dialect functor type
  * @see [[ConstraintVerifier.verifyFieldTypes]] for constraint verification using this typeclass
  */
trait HasFields[F[_]] {

  /**
    * Extracts the fields from a functor node.
    *
    * Returns an empty vector for nodes that do not carry fields.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the fields declared by this node
    */
  def fields[A](fa: F[A]): Vector[Field]
}

/**
  * Companion object for [[HasFields]], providing summoner.
  */
object HasFields {

  /**
    * Summons the implicit [[HasFields]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasFields]] instance
    */
  def apply[F[_]](implicit ev: HasFields[F]): HasFields[F] = ev
}

/**
  * Typeclass for dialect functors whose operations contain [[Func]] (method) definitions.
  *
  * Enables generic passes to inspect and transform methods across any dialect.
  *
  * @tparam F the dialect functor type
  */
trait HasMethods[F[_]] {

  /**
    * Extracts the functions/methods from a functor node.
    *
    * Returns an empty vector for nodes that do not carry methods.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the functions declared by this node
    */
  def functions[A](fa: F[A]): Vector[Func]
}

/**
  * Companion object for [[HasMethods]], providing summoner.
  */
object HasMethods {

  /**
    * Summons the implicit [[HasMethods]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasMethods]] instance
    */
  def apply[F[_]](implicit ev: HasMethods[F]): HasMethods[F] = ev
}

/**
  * Typeclass for dialect functors whose operations contain nested (child) declarations.
  *
  * The nested children are of type `A`, which is the recursive type parameter of the
  * functor, typically instantiated as `Fix[F]` in a concrete tree.
  *
  * @tparam F the dialect functor type
  */
trait HasNested[F[_]] {

  /**
    * Extracts the nested (child) declarations from a functor node.
    *
    * Returns an empty vector for nodes that do not carry nested declarations.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the nested child declarations
    */
  def nested[A](fa: F[A]): Vector[A]
}

/**
  * Companion object for [[HasNested]], providing summoner.
  */
object HasNested {

  /**
    * Summons the implicit [[HasNested]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasNested]] instance
    */
  def apply[F[_]](implicit ev: HasNested[F]): HasNested[F] = ev
}

/**
  * Typeclass for dialect functors whose operations carry a [[Visibility]] modifier.
  *
  * Enables generic passes to inspect and filter declarations by visibility.
  *
  * @tparam F the dialect functor type
  */
trait HasVisibility[F[_]] {

  /**
    * Extracts the visibility from a functor node.
    *
    * @tparam A the recursive type parameter of the functor
    * @param fa the functor node
    * @return the visibility of this declaration or operation
    */
  def visibility[A](fa: F[A]): Visibility
}

/**
  * Companion object for [[HasVisibility]], providing summoner.
  */
object HasVisibility {

  /**
    * Summons the implicit [[HasVisibility]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[HasVisibility]] instance
    */
  def apply[F[_]](implicit ev: HasVisibility[F]): HasVisibility[F] = ev
}
