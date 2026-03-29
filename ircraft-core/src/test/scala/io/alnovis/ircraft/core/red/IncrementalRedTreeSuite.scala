package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.red.IncrementalRedTree.DiffKind

class IncrementalRedTreeSuite extends munit.FunSuite:

  private def leaf(name: String): GenericOp =
    GenericOp(NodeKind("test", name))

  private def leafWithAttr(name: String, key: String, value: String): GenericOp =
    GenericOp(NodeKind("test", name), AttributeMap(Attribute.StringAttr(key, value)))

  private def container(name: String, children: Operation*): GenericOp =
    GenericOp(NodeKind("test", name), regions = Vector(Region("body", children.toVector)))

  test("unchanged module produces empty diff"):
    val module = Module("test", Vector(leaf("a"), leaf("b")))
    val diff   = IncrementalRedTree.diff(module, module)
    assertEquals(diff, Vector.empty)

  test("single leaf change produces Modified"):
    val old  = Module("test", Vector(leafWithAttr("a", "v", "1")))
    val now  = Module("test", Vector(leafWithAttr("a", "v", "2")))
    val diff = IncrementalRedTree.diff(old, now)
    assertEquals(diff.size, 1)
    assertEquals(diff.head.path, Vector(0))
    assertEquals(diff.head.kind, DiffKind.Modified)

  test("added operation produces Added"):
    val old  = Module("test", Vector(leaf("a")))
    val now  = Module("test", Vector(leaf("a"), leaf("b")))
    val diff = IncrementalRedTree.diff(old, now)
    assertEquals(diff.size, 1)
    assertEquals(diff.head.path, Vector(1))
    assertEquals(diff.head.kind, DiffKind.Added)

  test("removed operation produces Removed"):
    val old  = Module("test", Vector(leaf("a"), leaf("b")))
    val now  = Module("test", Vector(leaf("a")))
    val diff = IncrementalRedTree.diff(old, now)
    assertEquals(diff.size, 1)
    assertEquals(diff.head.path, Vector(1))
    assertEquals(diff.head.kind, DiffKind.Removed)

  test("nested change reports correct path"):
    val old  = Module("test", Vector(container("root", leafWithAttr("x", "v", "1"))))
    val now  = Module("test", Vector(container("root", leafWithAttr("x", "v", "2"))))
    val diff = IncrementalRedTree.diff(old, now)
    // root modified at [0], child modified at [0, 0]
    assert(diff.exists(d => d.path == Vector(0) && d.kind == DiffKind.Modified))
    assert(diff.exists(d => d.path == Vector(0, 0) && d.kind == DiffKind.Modified))

  test("unchanged sibling not in diff"):
    val old  = Module("test", Vector(leaf("a"), leafWithAttr("b", "v", "1")))
    val now  = Module("test", Vector(leaf("a"), leafWithAttr("b", "v", "2")))
    val diff = IncrementalRedTree.diff(old, now)
    // Only [1] should be modified, not [0]
    assert(!diff.exists(_.path == Vector(0)))
    assert(diff.exists(d => d.path == Vector(1) && d.kind == DiffKind.Modified))

  test("multiple changes detected independently"):
    val old = Module(
      "test",
      Vector(
        leafWithAttr("a", "v", "1"),
        leaf("b"),
        leafWithAttr("c", "v", "1")
      )
    )
    val now = Module(
      "test",
      Vector(
        leafWithAttr("a", "v", "2"),
        leaf("b"),
        leafWithAttr("c", "v", "2")
      )
    )
    val diff = IncrementalRedTree.diff(old, now)
    assertEquals(diff.size, 2)
    assert(diff.exists(_.path == Vector(0)))
    assert(diff.exists(_.path == Vector(2)))

  test("rebuild returns fresh Red Tree"):
    val old     = Module("test", Vector(leaf("a")))
    val now     = Module("test", Vector(leaf("a"), leaf("b")))
    val oldRoot = RedTree.from(old)
    val newRoot = IncrementalRedTree.rebuild(oldRoot, now)
    assertEquals(newRoot.children.size, 2)
