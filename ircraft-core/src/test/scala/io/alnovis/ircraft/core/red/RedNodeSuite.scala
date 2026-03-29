package io.alnovis.ircraft.core.red

import io.alnovis.ircraft.core.*

class RedNodeSuite extends munit.FunSuite:

  // Helpers
  private def leaf(name: String): GenericOp =
    GenericOp(NodeKind("test", name))

  private def container(name: String, children: Operation*): GenericOp =
    GenericOp(NodeKind("test", name), regions = Vector(Region("body", children.toVector)))

  // -- Basic construction ---------------------------------------------------

  test("empty module has no children"):
    val root = RedTree.from(IrModule.empty("test"))
    assertEquals(root.children.size, 0)
    assertEquals(root.offset, 0)
    assertEquals(root.depth, 0)

  test("root has no parent"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"))))
    assertEquals(root.parent, None)

  test("child has parent pointing to root"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"))))
    val child = root.children.head
    assert(child.parent.isDefined)
    assert(child.parent.get eq root.asInstanceOf[RedNode[Operation]])

  // -- Offsets --------------------------------------------------------------

  test("root offset is 0"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    assertEquals(root.offset, 0)

  test("first child offset is 1"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    assertEquals(root.children(0).offset, 1)

  test("second child offset follows first"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    val first = root.children(0)
    assertEquals(root.children(1).offset, first.endOffset)

  test("nested offsets are cumulative"):
    val inner    = container("mid", leaf("leaf"))
    val root     = RedTree.from(IrModule("test", Vector(inner)))
    val mid      = root.children(0)
    val deepLeaf = mid.children(0)
    assertEquals(root.offset, 0)
    assertEquals(mid.offset, 1)
    assertEquals(deepLeaf.offset, 2)

  test("endOffset equals offset + nodeSize"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"))))
    val child = root.children(0)
    assertEquals(child.endOffset, child.offset + 1) // leaf size = 1

  // -- Depth ----------------------------------------------------------------

  test("depth: root=0, child=1, grandchild=2"):
    val inner = container("mid", leaf("deep"))
    val root  = RedTree.from(IrModule("test", Vector(inner)))
    assertEquals(root.depth, 0)
    assertEquals(root.children(0).depth, 1)
    assertEquals(root.children(0).children(0).depth, 2)

  // -- Ancestors ------------------------------------------------------------

  test("ancestors from leaf to root"):
    val inner    = container("mid", leaf("deep"))
    val root     = RedTree.from(IrModule("test", Vector(inner)))
    val deepNode = root.children(0).children(0)
    val path     = deepNode.ancestors
    assertEquals(path.size, 3)
    assertEquals(path.head.green.kind.qualifiedName, "builtin.module")
    assertEquals(path.last.green.kind.qualifiedName, "test.deep")

  // -- Siblings -------------------------------------------------------------

  test("nextSibling navigates forward"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"), leaf("c"))))
    val a    = root.children(0)
    val b    = a.nextSibling
    assert(b.isDefined)
    assertEquals(b.get.green.kind.name, "b")

  test("nextSibling returns None for last child"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    val last = root.children(1)
    assertEquals(last.nextSibling, None)

  test("prevSibling navigates backward"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    val b    = root.children(1)
    val a    = b.prevSibling
    assert(a.isDefined)
    assertEquals(a.get.green.kind.name, "a")

  test("prevSibling returns None for first child"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"))))
    assertEquals(root.children(0).prevSibling, None)

  // -- Find -----------------------------------------------------------------

  test("find locates matching descendant"):
    val inner = container("mid", leaf("target"))
    val root  = RedTree.from(IrModule("test", Vector(leaf("other"), inner)))
    val found = root.find(_.kind.name == "target")
    assert(found.isDefined)
    assertEquals(found.get.green.kind.name, "target")

  test("find returns None when no match"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"))))
    assertEquals(root.find(_.kind.name == "missing"), None)

  // -- Locate by position ---------------------------------------------------

  test("locate finds root at position 0"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"))))
    val found = RedTree.locate(root, 0)
    assert(found.isDefined)
    assertEquals(found.get.green.kind.qualifiedName, "builtin.module")

  test("locate finds leaf by offset"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"), leaf("b"))))
    val aNode = root.children(0)
    val found = RedTree.locate(root, aNode.offset)
    assert(found.isDefined)
    assertEquals(found.get.green.kind.name, "a")

  test("locate finds deep node"):
    val inner    = container("mid", leaf("deep"))
    val root     = RedTree.from(IrModule("test", Vector(inner)))
    val deepNode = root.children(0).children(0)
    val found    = RedTree.locate(root, deepNode.offset)
    assert(found.isDefined)
    assertEquals(found.get.green.kind.name, "deep")

  test("locate returns None for out-of-bounds"):
    val root = RedTree.from(IrModule("test", Vector(leaf("a"))))
    assertEquals(RedTree.locate(root, 999), None)

  // -- collectWithContext ---------------------------------------------------

  test("collectWithContext finds all matching nodes"):
    val root  = RedTree.from(IrModule("test", Vector(leaf("a"), container("mid", leaf("a")), leaf("b"))))
    val found = RedTree.collectWithContext(root, _.kind.name == "a")
    assertEquals(found.size, 2)
    // Both have ancestors
    assert(found.forall(_.ancestors.nonEmpty))

  // -- Content hash ---------------------------------------------------------

  test("contentHash delegates to green"):
    val op   = leaf("x")
    val root = RedTree.from(IrModule("test", Vector(op)))
    assertEquals(root.children(0).contentHash, op.contentHash)
