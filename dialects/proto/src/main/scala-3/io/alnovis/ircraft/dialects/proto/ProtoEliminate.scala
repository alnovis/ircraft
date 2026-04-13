package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{ :+:, Fix, eliminate }
import io.alnovis.ircraft.core.ir.SemanticF

/** Scala 3 version: type alias + eliminateProto using :+: syntax. */
object ProtoEliminate:
  type ProtoIR = ProtoF :+: SemanticF

  val eliminateProto: Fix[ProtoIR] => Fix[SemanticF] =
    eliminate.dialect(ProtoDialect.protoToSemantic)
