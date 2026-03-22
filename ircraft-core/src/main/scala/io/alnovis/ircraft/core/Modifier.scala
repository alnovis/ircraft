package io.alnovis.ircraft.core

/** Language-agnostic access and declaration modifiers. */
enum Modifier:
  case Public, Private, Protected, PackagePrivate
  case Abstract, Final, Static
  case Sealed, Override, Default
  case Synchronized, Volatile, Transient, Native

object Modifier:
  given ContentHashable[Modifier] with
    def contentHash(a: Modifier): Int = a.ordinal

  given ContentHashable[Set[Modifier]] with
    def contentHash(a: Set[Modifier]): Int = ContentHash.ofSet(a)
