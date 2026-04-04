package io.alnovis.ircraft.core.ir

/** Structured documentation, language-agnostic. Attached to declarations via Meta. */
case class Doc(
  summary: String,
  description: Option[String] = None,
  params: Vector[(String, String)] = Vector.empty,
  returns: Option[String] = None,
  throws: Vector[(String, String)] = Vector.empty,
  tags: Vector[(String, String)] = Vector.empty,
  examples: Vector[String] = Vector.empty
)

object Doc {
  val key: Meta.Key[Doc] = Meta.Key("doc")
}
