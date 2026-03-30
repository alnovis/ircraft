package io.alnovis.ircraft.dialect.openapi.ops

import io.alnovis.ircraft.core.ContentHashable

enum HttpMethod:
  case Get, Post, Put, Delete, Patch, Options, Head, Trace

object HttpMethod:
  given ContentHashable[HttpMethod] with
    def contentHash(a: HttpMethod): Int = a.ordinal

enum ParameterLocation:
  case Query, Path, Header, Cookie

object ParameterLocation:
  given ContentHashable[ParameterLocation] with
    def contentHash(a: ParameterLocation): Int = a.ordinal

enum SecuritySchemeType:
  case ApiKey, Http, OAuth2, OpenIdConnect

object SecuritySchemeType:
  given ContentHashable[SecuritySchemeType] with
    def contentHash(a: SecuritySchemeType): Int = a.ordinal

enum CompositionKind:
  case OneOf, AnyOf, AllOf

object CompositionKind:
  given ContentHashable[CompositionKind] with
    def contentHash(a: CompositionKind): Int = a.ordinal
