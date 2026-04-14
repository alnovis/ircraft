package io.alnovis.ircraft.core.ir

/**
  * Structured, language-agnostic documentation attached to declarations via [[Meta]].
  *
  * [[Doc]] captures the essential elements of API documentation (summary, description,
  * parameter docs, return value docs, exception docs, tags, and examples) in a
  * structured form that can be rendered into Javadoc, Scaladoc, KDoc, or any other
  * documentation format during code generation.
  *
  * Attach a [[Doc]] to a declaration by storing it in the declaration's [[Meta]]
  * under [[Doc.key]]:
  *
  * {{{
  * val doc = Doc(
  *   summary = "Converts a name to uppercase.",
  *   params  = Vector(("name", "the input name")),
  *   returns = Some("the uppercased name")
  * )
  * val meta = Meta.empty.set(Doc.key, doc)
  * }}}
  *
  * @param summary     a one-line summary of the documented element
  * @param description an optional extended description (may contain multiple paragraphs)
  * @param params      parameter documentation as `(name, description)` pairs
  * @param returns     an optional description of the return value
  * @param throws      exception documentation as `(exceptionType, description)` pairs
  * @param tags        additional documentation tags as `(tagName, value)` pairs
  * @param examples    code example strings to include in the documentation
  * @see [[Doc.key]] for the [[Meta.Key]] used to attach docs to declarations
  */
case class Doc(
  summary: String,
  description: Option[String] = None,
  params: Vector[(String, String)] = Vector.empty,
  returns: Option[String] = None,
  throws: Vector[(String, String)] = Vector.empty,
  tags: Vector[(String, String)] = Vector.empty,
  examples: Vector[String] = Vector.empty
)

/**
  * Companion object for [[Doc]], providing the [[Meta.Key]] used to attach
  * documentation to declaration metadata.
  */
object Doc {

  /**
    * The [[Meta.Key]] under which [[Doc]] instances are stored in a declaration's [[Meta]].
    *
    * {{{
    * val doc: Option[Doc] = decl.meta.get(Doc.key)
    * }}}
    */
  val key: Meta.Key[Doc] = Meta.Key("doc")
}
