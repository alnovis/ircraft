package io.alnovis.ircraft.dialect.proto.lowering

/**
  * Attribute keys attached to Semantic IR during Proto -> Semantic lowering.
  *
  * Subsequent passes (ConflictResolutionPass, HasMethodsPass, etc.) read these attributes to make decisions without
  * importing proto dialect operations.
  */
object ProtoAttributes:

  // ── On FileOp (for incremental generation) ─────────────────────────────

  /** Source entity name (message or enum) that produced this FileOp. Global files have no sourceEntity. */
  val SourceEntity = "proto.sourceEntity"

  // ── On InterfaceOp / ClassOp (message-level) ───────────────────────────

  /** List of version identifiers where this message exists. */
  val PresentInVersions = "proto.presentInVersions"

  /** Proto package pattern for resolving proto class names. */
  val ProtoPackagePattern = "proto.packagePattern"

  /** List of all schema version identifiers. */
  val SchemaVersions = "proto.schemaVersions"

  /** Version this impl class belongs to (on impl ClassOp only). */
  val ImplVersion = "proto.implVersion"

  /** Proto class name for this version (on impl ClassOp only). */
  val ProtoClassName = "proto.className"

  // ── On MethodOp / FieldDeclOp (field-level) ────────────────────────────

  /** Conflict type string (matches ConflictType enum name). */
  val ConflictType = "proto.conflictType"

  /** Whether this field is optional in any version. */
  val IsOptional = "proto.isOptional"

  /** Whether this field is repeated. */
  val IsRepeated = "proto.isRepeated"

  /** Whether this field is a map. */
  val IsMap = "proto.isMap"

  /** Original proto field name (snake_case). */
  val ProtoFieldName = "proto.fieldName"

  /** Proto field number. */
  val FieldNumber = "proto.fieldNumber"

  /** Well-known type identifier (e.g., "Timestamp", "Duration", "Struct"). Empty if not a WKT. */
  val WellKnownType = "proto.wellKnownType"

  /** Versions where this field exists (subset of message versions). */
  val FieldPresentInVersions = "proto.fieldPresentInVersions"
