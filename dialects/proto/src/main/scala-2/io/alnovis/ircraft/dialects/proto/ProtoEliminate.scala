package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{Coproduct, Fix, eliminate}
import io.alnovis.ircraft.core.ir.SemanticF

/** Scala 2 version: type alias + eliminateProto using kind-projector. */
object ProtoEliminate {
  type ProtoIR[A] = Coproduct[ProtoF, SemanticF, A]

  val eliminateProto: Fix[ProtoIR] => Fix[SemanticF] =
    eliminate.dialect(ProtoDialect.protoToSemantic)
}
