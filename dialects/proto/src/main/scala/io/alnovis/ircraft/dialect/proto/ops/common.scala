package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.ContentHashable

/** Protobuf syntax version. */
enum ProtoSyntax:
  case Proto2, Proto3

object ProtoSyntax:

  given ContentHashable[ProtoSyntax] with
    def contentHash(a: ProtoSyntax): Int = a.ordinal
