package io.alnovis.ircraft.dialect.graphql.ops

import io.alnovis.ircraft.core.ContentHashable

/** GraphQL directive location -- where a directive can be applied. */
enum DirectiveLocation:
  // Type system locations
  case Schema, Scalar, Object, FieldDefinition, ArgumentDefinition,
       Interface, Union, Enum, EnumValue, InputObject, InputFieldDefinition
  // Execution locations
  case Query, Mutation, Subscription, Field, FragmentDefinition,
       FragmentSpread, InlineFragment, VariableDefinition

object DirectiveLocation:

  given ContentHashable[DirectiveLocation] with
    def contentHash(a: DirectiveLocation): Int = a.ordinal
