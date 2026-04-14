package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{Coproduct, Fix, eliminate}
import io.alnovis.ircraft.core.ir.SemanticF

/**
  * Scala 2 dialect elimination for [[ProtoF]].
  *
  * Provides a type alias for the combined Proto+Semantic IR and the
  * elimination function that lowers [[ProtoF]] nodes into
  * [[io.alnovis.ircraft.core.ir.SemanticF]] nodes.
  *
  * Uses the kind-projector plugin for partial type application in
  * the [[ProtoIR]] type alias.
  *
  * @see [[ProtoF]] for the protobuf dialect functor being eliminated
  * @see [[ProtoDialect]] for the algebra that defines the Proto-to-Semantic translation
  * @see [[io.alnovis.ircraft.core.algebra.eliminate]] for the generic elimination mechanism
  */
object ProtoEliminate {

  /**
    * Type alias for the combined IR coproduct of [[ProtoF]] and
    * [[io.alnovis.ircraft.core.ir.SemanticF]].
    *
    * @tparam A the recursive carrier type
    */
  type ProtoIR[A] = Coproduct[ProtoF, SemanticF, A]

  /**
    * The elimination function that lowers a `Fix[ProtoIR]` tree into
    * a pure `Fix[SemanticF]` tree by translating all [[ProtoF]] nodes
    * into their [[io.alnovis.ircraft.core.ir.SemanticF]] equivalents.
    *
    * Internally, this delegates to [[io.alnovis.ircraft.core.algebra.eliminate.dialect]]
    * with the translation algebra from [[ProtoDialect.protoToSemantic]].
    *
    * @see [[ProtoDialect.protoToSemantic]]
    */
  val eliminateProto: Fix[ProtoIR] => Fix[SemanticF] =
    eliminate.dialect(ProtoDialect.protoToSemantic)
}
