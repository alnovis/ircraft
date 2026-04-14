package io.alnovis.ircraft.dialects.proto

import io.alnovis.ircraft.core.algebra.{ Coproduct, Fix, eliminate, scheme }
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.dialects.proto.ProtoF._

/**
  * Proto dialect integration: provides the lowering algebra that converts proto-specific
  * IR nodes (`ProtoF`) into standard semantic IR nodes (`SemanticF`).
  *
  * This object contains [[protoToSemantic]], an `Algebra[ProtoF, Fix[SemanticF]]` that
  * maps each `ProtoF` variant to its `SemanticF` equivalent:
  *
  *  - `MessageNodeF` -> `TypeDeclF` with `TypeKind.Protocol` (interface-like)
  *  - `EnumNodeF` -> `EnumDeclF` with enum variants
  *  - `OneofNodeF` -> `TypeDeclF` with `TypeKind.Sum` (sealed union)
  *
  * The `eliminateProto` function (defined in platform-specific source directories
  * `scala-3/` and `scala-2/` due to type lambda syntax differences) uses this algebra
  * to eliminate all `ProtoF` nodes from a mixed coproduct IR, producing a pure
  * `Fix[SemanticF]` tree.
  *
  * @see [[ProtoLowering.lowerToMixed]] for producing mixed IR with `ProtoF` nodes
  * @see [[ProtoMeta]] for metadata keys attached during lowering
  */
object ProtoDialect {

  /**
    * Lowering algebra that converts each `ProtoF` node to a `SemanticF` subtree.
    *
    * The conversion preserves all metadata (`Meta`) from the proto nodes.
    *
    *  - `MessageNodeF` -> `TypeDeclF(Protocol)` with fields, functions, and nested declarations
    *  - `EnumNodeF` -> `EnumDeclF` with variants
    *  - `OneofNodeF` -> `TypeDeclF(Sum)` with fields representing the union alternatives
    *
    * @return the algebra that maps `ProtoF[Fix[SemanticF]]` to `Fix[SemanticF]`
    *
    * @example {{{
    * // Typically used with eliminate.dialect:
    * val pure: Fix[SemanticF] = eliminate.dialect(mixed, ProtoDialect.protoToSemantic)
    * }}}
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
