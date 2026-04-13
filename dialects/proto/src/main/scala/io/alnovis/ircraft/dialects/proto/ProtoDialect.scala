package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{ Coproduct, Fix, eliminate, scheme }
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.dialects.proto.ProtoF._

/** Proto dialect integration -- type aliases, lowering algebra, elimination. */
object ProtoDialect {

  /**
    * Lowering algebra: converts each ProtoF operation to a SemanticF subtree.
    *
    *  - MessageNodeF -> TypeDeclF(Protocol) with fields, functions, nested
    *  - EnumNodeF -> EnumDeclF with variants
    *  - OneofNodeF -> TypeDeclF(Sum) with fields (sealed union)
    */
  val protoToSemantic: Algebra[ProtoF, Fix[SemanticF]] = {
    case MessageNodeF(name, fields, functions, nested, meta) =>
      Fix[SemanticF](TypeDeclF(name, TypeKind.Protocol, fields, functions, nested, meta = meta))
    case EnumNodeF(name, variants, meta) =>
      Fix[SemanticF](EnumDeclF(name, variants, meta = meta))
    case OneofNodeF(name, fields, meta) =>
      Fix[SemanticF](TypeDeclF(name, TypeKind.Sum, fields, meta = meta))
  }

  // eliminateProto is in scala-3/ and scala-2/ due to type lambda syntax differences
}
