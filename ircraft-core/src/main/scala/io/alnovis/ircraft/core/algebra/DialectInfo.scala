package io.alnovis.ircraft.core.algebra

/**
  * Metadata typeclass for a dialect functor.
  *
  * Provides a human-readable name and the number of operations (case classes)
  * in the dialect. This is a lightweight typeclass -- no macro derivation needed,
  * just two fields to implement.
  *
  * {{{
  * // Scala 3:
  * given DialectInfo[MyDialectF] = DialectInfo("MyDialect", 3)
  *
  * // Scala 2:
  * implicit val myDialectInfo: DialectInfo[MyDialectF] = DialectInfo("MyDialect", 3)
  * }}}
  *
  * @tparam F the dialect functor type
  * @see [[HasName]] for per-node name extraction
  * @see [[HasMeta]] for per-node metadata access
  */
trait DialectInfo[F[_]] {

  /**
    * The human-readable name of the dialect (e.g., "SemanticF", "LLVM_IR").
    *
    * @return the dialect name
    */
  def dialectName: String

  /**
    * The number of distinct operations (case classes / variants) in the dialect.
    *
    * @return the number of operations
    */
  def operationCount: Int
}

/**
  * Companion object for [[DialectInfo]], providing a summoner and a factory method.
  */
object DialectInfo {

  /**
    * Summons the implicit [[DialectInfo]] instance for `F`.
    *
    * @tparam F the dialect functor type
    * @param ev the implicit instance
    * @return the [[DialectInfo]] instance
    */
  def apply[F[_]](implicit ev: DialectInfo[F]): DialectInfo[F] = ev

  /**
    * Creates a [[DialectInfo]] instance from a name and operation count.
    *
    * {{{
    * implicit val info: DialectInfo[MyF] = DialectInfo("MyDialect", 5)
    * }}}
    *
    * @tparam F the dialect functor type
    * @param name the human-readable dialect name
    * @param ops  the number of operations in the dialect
    * @return a new [[DialectInfo]] instance
    */
  def apply[F[_]](name: String, ops: Int): DialectInfo[F] = {
    val n = name
    val o = ops
    new DialectInfo[F] {
      def dialectName: String = n
      def operationCount: Int = o
    }
  }
}
