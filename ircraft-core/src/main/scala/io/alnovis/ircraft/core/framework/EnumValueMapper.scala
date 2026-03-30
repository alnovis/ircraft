package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.TypeRef

trait EnumValueMapper[V]:
  extension (v: V)
    def constantName: String
    def constantArguments: List[(String, TypeRef)]

object EnumValueMapper:
  case class IntValued(name: String, value: Int)
  given intValuedMapper: EnumValueMapper[IntValued] with
    extension (v: IntValued)
      def constantName: String = v.name
      def constantArguments: List[(String, TypeRef)] = List(v.value.toString -> TypeRef.INT)

  case class StringValued(name: String, value: String)
  given stringValuedMapper: EnumValueMapper[StringValued] with
    extension (v: StringValued)
      def constantName: String = v.name
      def constantArguments: List[(String, TypeRef)] = List(v.value -> TypeRef.STRING)

  case class Simple(name: String)
  given simpleMapper: EnumValueMapper[Simple] with
    extension (v: Simple)
      def constantName: String = v.name
      def constantArguments: List[(String, TypeRef)] = Nil
