package io.alnovis.ircraft.core

class PipelineSuite extends munit.FunSuite:

  // A simple test Operation
  case class TestOp(value: String) extends Operation:
    val kind: NodeKind           = NodeKind("test", "op")
    val attributes: AttributeMap = AttributeMap(Attribute.StringAttr("value", value))
    val regions: Vector[Region]  = Vector.empty
    val span: Option[Span]       = None
    lazy val contentHash: Int    = ContentHash.ofString(value)
    val estimatedSize: Int       = value.length

  val ctx: PassContext = PassContext()

  test("identity pass returns module unchanged"):
    val identityPass = new Pass:
      val name                                                  = "identity"
      val description                                           = "does nothing"
      def run(module: Module, context: PassContext): PassResult = PassResult(module)

    val module = Module("test", Vector(TestOp("hello")))
    val result = identityPass.run(module, ctx)
    assertEquals(result.module.name, "test")
    assert(result.isSuccess)

  test("pipeline executes passes in order"):
    var order = Vector.empty[String]

    def makePass(n: String): Pass = new Pass:
      val name        = n
      val description = s"pass $n"
      def run(module: Module, context: PassContext): PassResult =
        order = order :+ n
        PassResult(module)

    val pipeline = Pipeline("test-pipeline", makePass("a"), makePass("b"), makePass("c"))
    pipeline.run(Module.empty("test"), ctx)

    assertEquals(order, Vector("a", "b", "c"))

  test("pipeline stops on error when failFast"):
    var executed = Vector.empty[String]

    val passOk = new Pass:
      val name        = "ok"
      val description = "succeeds"
      def run(module: Module, context: PassContext): PassResult =
        executed = executed :+ name
        PassResult(module)

    val passFail = new Pass:
      val name        = "fail"
      val description = "fails"
      def run(module: Module, context: PassContext): PassResult =
        executed = executed :+ name
        PassResult(module, List(DiagnosticMessage.error("boom")))

    val passAfter = new Pass:
      val name        = "after"
      val description = "should not run"
      def run(module: Module, context: PassContext): PassResult =
        executed = executed :+ name
        PassResult(module)

    val pipeline = Pipeline("test", Vector(passOk, passFail, passAfter), failFast = true)
    val result   = pipeline.run(Module.empty("test"), ctx)

    assert(result.hasErrors)
    assertEquals(executed, Vector("ok", "fail"))

  test("pipeline skips disabled passes"):
    var executed = Vector.empty[String]

    val enabledPass = new Pass:
      val name        = "enabled"
      val description = "runs"
      def run(module: Module, context: PassContext): PassResult =
        executed = executed :+ name
        PassResult(module)

    val disabledPass = new Pass:
      val name                                              = "disabled"
      val description                                       = "skipped"
      override def isEnabled(context: PassContext): Boolean = false
      def run(module: Module, context: PassContext): PassResult =
        executed = executed :+ name
        PassResult(module)

    val pipeline = Pipeline("test", Vector(enabledPass, disabledPass, enabledPass))
    pipeline.run(Module.empty("test"), ctx)

    assertEquals(executed, Vector("enabled", "enabled"))

  test("pipeline collects diagnostics from all passes"):
    val pass1 = new Pass:
      val name        = "p1"
      val description = "warns"
      def run(module: Module, context: PassContext): PassResult =
        PassResult(module, List(DiagnosticMessage.warning("w1")))

    val pass2 = new Pass:
      val name        = "p2"
      val description = "warns"
      def run(module: Module, context: PassContext): PassResult =
        PassResult(module, List(DiagnosticMessage.warning("w2")))

    val pipeline = Pipeline("test", Vector(pass1, pass2))
    val result   = pipeline.run(Module.empty("test"), ctx)

    assert(result.isSuccess)
    assertEquals(result.warnings.size, 2)

  test("Module.collect finds operations recursively"):
    val op1    = TestOp("a")
    val op2    = TestOp("b")
    val module = Module("test", Vector(op1, op2))

    val found = module.collect { case t: TestOp => t }
    assertEquals(found.size, 2)
    assertEquals(found.map(_.value), Vector("a", "b"))

  test("pipeline andThen composes"):
    val p1 = Pipeline("first", Vector.empty)
    val pass = new Pass:
      val name                                                  = "x"
      val description                                           = "x"
      def run(module: Module, context: PassContext): PassResult = PassResult(module)

    val p2 = p1.andThen(pass)
    assertEquals(p2.passes.size, 1)
