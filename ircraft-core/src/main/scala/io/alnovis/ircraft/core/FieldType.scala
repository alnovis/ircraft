package io.alnovis.ircraft.core

/** Describes the expected type of a field in a GenericDialect operation schema. */
enum FieldType:
  case StringField
  case IntField
  case LongField
  case BoolField
  case StringListField
