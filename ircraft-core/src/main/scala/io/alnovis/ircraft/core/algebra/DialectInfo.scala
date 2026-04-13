package io.alnovis.ircraft.core.algebra

/** Metadata about a dialect functor.
  *
  * Provides human-readable name and number of operations (case classes) in the dialect.
  * This is a lightweight typeclass -- no macro derivation needed, just 2 fields.
  *
  * {{{
  * // Scala 3:
  * given DialectInfo[MyDialectF] = DialectInfo("MyDialect", 3)
  *
  * // Scala 2:
  * implicit val myDialectInfo: DialectInfo[MyDialectF] = DialectInfo("MyDialect", 3)
  * }}}
  */
trait DialectInfo[F[_]] {
  def dialectName: String
  def operationCount: Int
}

object DialectInfo {

  def apply[F[_]](implicit ev: DialectInfo[F]): DialectInfo[F] = ev

  def apply[F[_]](name: String, ops: Int): DialectInfo[F] = {
    val n = name
    val o = ops
    new DialectInfo[F] {
      def dialectName: String = n
      def operationCount: Int = o
    }
  }
}
