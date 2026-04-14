package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{ :+:, Fix, eliminate }
import io.alnovis.ircraft.core.ir.SemanticF

/**
  * Proto dialect elimination entry point (Scala 3 version).
  *
  * Provides the [[ProtoIR]] type alias for the composed `ProtoF :+: SemanticF` IR,
  * and the [[eliminateProto]] function that lowers all [[ProtoF]] nodes into
  * [[io.alnovis.ircraft.core.ir.SemanticF]] via [[io.alnovis.ircraft.core.algebra.eliminate.dialect]].
  *
  * {{{
  * val composedTree: Fix[ProtoIR] = ...
  * val semanticTree: Fix[SemanticF] = ProtoEliminate.eliminateProto(composedTree)
  * }}}
  *
  * @see [[ProtoDialect]] for the lowering algebra that defines how each [[ProtoF]] variant maps to [[io.alnovis.ircraft.core.ir.SemanticF]]
  * @see [[io.alnovis.ircraft.core.algebra.eliminate.dialect]] for the generic elimination mechanism
  */
object ProtoEliminate:

  /**
    * Type alias for the composed IR containing both [[ProtoF]] and [[io.alnovis.ircraft.core.ir.SemanticF]] nodes.
    *
    * Equivalent to `[A] =>> Coproduct[ProtoF, SemanticF, A]`.
    */
  type ProtoIR = ProtoF :+: SemanticF

  /**
    * Eliminates all [[ProtoF]] nodes from a `Fix[ProtoIR]` tree, producing a pure `Fix[SemanticF]` tree.
    *
    * Uses [[ProtoDialect.protoToSemantic]] as the lowering algebra and
    * [[io.alnovis.ircraft.core.algebra.eliminate.dialect]] for the catamorphism.
    *
    * @return a function that converts `Fix[ProtoIR]` to `Fix[SemanticF]`
    */
  val eliminateProto: Fix[ProtoIR] => Fix[SemanticF] =
    eliminate.dialect(ProtoDialect.protoToSemantic)
