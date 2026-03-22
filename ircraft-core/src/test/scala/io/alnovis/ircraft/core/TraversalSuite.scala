package io.alnovis.ircraft.core

import scala.annotation.targetName

import io.alnovis.ircraft.core.Traversal.*

class TraversalSuite extends munit.FunSuite:

  case class ParentOp(name: String, override val regions: Vector[Region]) extends Operation:
    val kind: NodeKind               = NodeKind("test", "parent")
    val attributes: AttributeMap     = AttributeMap.empty
    val span: Option[Span]           = None
    lazy val kids: Vector[Operation] = region("children").map(_.operations).getOrElse(Vector.empty)
    lazy val contentHash: Int        = ContentHash.ofString(name)
    val width: Int                   = 1

    override def mapChildren(f: Operation => Operation): ParentOp =
      copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  object ParentOp:

    @targetName("create")
    def apply(name: String, kids: Vector[Operation]): ParentOp =
      new ParentOp(name, Vector(Region("children", kids)))

  case class LeafOp(name: String) extends Operation:
    val kind: NodeKind           = NodeKind("test", "leaf")
    val attributes: AttributeMap = AttributeMap.empty
    val regions: Vector[Region]  = Vector.empty
    val span: Option[Span]       = None
    lazy val contentHash: Int    = ContentHash.ofString(name)
    val width: Int               = 1

  val tree: ParentOp = ParentOp(
    "root",
    Vector(
      ParentOp("a", Vector(LeafOp("a1"), LeafOp("a2"))),
      LeafOp("b"),
      ParentOp("c", Vector(LeafOp("c1")))
    )
  )

  val module: Module = Module("test", Vector(tree))

  // ── Operation.walk ─────────────────────────────────────────────────────

  test("walk visits all nodes depth-first"):
    var visited = Vector.empty[String]
    tree.walk:
      case p: ParentOp => visited = visited :+ p.name
      case l: LeafOp   => visited = visited :+ l.name

    assertEquals(visited, Vector("root", "a", "a1", "a2", "b", "c", "c1"))

  test("walk on leaf visits only self"):
    var visited = Vector.empty[String]
    LeafOp("solo").walk { case l: LeafOp => visited = visited :+ l.name }
    assertEquals(visited, Vector("solo"))

  // ── Operation.collectAll ───────────────────────────────────────────────

  test("collectAll finds all leaves"):
    val leaves = tree.collectAll { case l: LeafOp => l.name }
    assertEquals(leaves, Vector("a1", "a2", "b", "c1"))

  test("collectAll finds all parents"):
    val parents = tree.collectAll { case p: ParentOp => p.name }
    assertEquals(parents, Vector("root", "a", "c"))

  test("collectAll returns empty for no matches"):
    val none = tree.collectAll { case _: Module => () }
    assertEquals(none, Vector.empty)

  // ── Operation.size ─────────────────────────────────────────────────────

  test("size counts all nodes in subtree"):
    assertEquals(tree.size, 7)

  test("size of leaf is 1"):
    assertEquals(LeafOp("x").size, 1)

  // ── Module.walkAll ─────────────────────────────────────────────────────

  test("Module.walkAll visits all operations"):
    var visited = Vector.empty[String]
    module.walkAll:
      case p: ParentOp => visited = visited :+ p.name
      case l: LeafOp   => visited = visited :+ l.name

    assertEquals(visited, Vector("root", "a", "a1", "a2", "b", "c", "c1"))

  test("Module.walkAll on empty module visits nothing"):
    var count = 0
    Module.empty("test").walkAll(_ => count += 1)
    assertEquals(count, 0)

  // ── Module.transformTopLevel ───────────────────────────────────────────

  test("transformTopLevel replaces matching top-level ops"):
    val flat = Module("test", Vector(LeafOp("a"), LeafOp("b"), LeafOp("c")))
    val transformed = flat.transformTopLevel {
      case l: LeafOp if l.name == "b" =>
        l.copy(name = "B")
    }
    val names = transformed.topLevel.collect { case l: LeafOp => l.name }
    assertEquals(names, Vector("a", "B", "c"))

  test("transformTopLevel preserves non-matching ops"):
    val flat = Module("test", Vector(LeafOp("a"), LeafOp("b")))
    val transformed = flat.transformTopLevel {
      case l: LeafOp if l.name == "x" =>
        l.copy(name = "X")
    }
    val names = transformed.topLevel.collect { case l: LeafOp => l.name }
    assertEquals(names, Vector("a", "b"))

  // ── Module.transform (deep) ────────────────────────────────────────────

  test("transform applies to deeply nested leaf ops"):
    val transformed = module.transform {
      case l: LeafOp =>
        l.copy(name = l.name.toUpperCase)
    }
    val leaves = transformed.collect { case l: LeafOp => l }.map(_.name)
    assertEquals(leaves, Vector("A1", "A2", "B", "C1"))

  test("transform preserves parent structure"):
    val transformed = module.transform {
      case l: LeafOp =>
        l.copy(name = "x")
    }
    val parents = transformed.collect { case p: ParentOp => p }.map(_.name)
    assertEquals(parents, Vector("root", "a", "c"))

  test("transform can replace parent ops"):
    val flat = Module(
      "test",
      Vector(
        ParentOp("keep", Vector(LeafOp("child"))),
        ParentOp("replace", Vector(LeafOp("child")))
      )
    )
    val transformed = flat.transform {
      case p: ParentOp if p.name == "replace" =>
        LeafOp("replaced")
    }
    assertEquals(transformed.topLevel.size, 2)
    val names = transformed.topLevel.map:
      case p: ParentOp => p.name
      case l: LeafOp   => l.name
    assertEquals(names, Vector("keep", "replaced"))

  test("deepTransform on single operation"):
    val nested = ParentOp("root", Vector(LeafOp("a"), LeafOp("b")))
    val result = nested.deepTransform {
      case l: LeafOp =>
        l.copy(name = l.name + "!")
    }
    val leaves = result.collectAll { case l: LeafOp => l.name }
    assertEquals(leaves, Vector("a!", "b!"))
