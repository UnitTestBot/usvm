package org.usvm.mcp

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.mcp.exec.TsAnalysisRunner
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.incomplete
import org.usvm.mcp.json.toDiagnosticsDto
import org.usvm.mcp.json.toTestCaseDto
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.SceneCache
import org.usvm.mcp.tools.toCounterexampleResult
import org.usvm.mcp.tools.toExceptionsResult
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AnalysisOutcomeTest {
    @TempDir
    lateinit var directory: Path

    private val runner = TsAnalysisRunner()

    @BeforeEach
    fun requireNativeFrontend() {
        assumeTrue(EtsIrProvider.default() == EtsIrProvider.TS_FRONTEND)
    }

    @Test
    fun `unsupported source is incomplete rather than an ordinary negative`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "unsupportedAccessor")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val counterexampleResult = outcome.toCounterexampleResult(method)
        val exceptionsResult = outcome.toExceptionsResult(method)

        assertTrue(outcome.unsupportedPaths.isNotEmpty())
        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = counterexampleResult.verdict)
        assertEquals(expected = outcome.unsupportedPaths, actual = counterexampleResult.analysis.unsupportedPaths)
        assertEquals(expected = outcome.unsupportedPaths, actual = exceptionsResult.analysis.unsupportedPaths)
        assertTrue(counterexampleResult.counterexamples.isEmpty())
        assertTrue(exceptionsResult.exceptionalCases.isEmpty())
        assertTrue(exceptionsResult.analysis.incomplete)
    }

    @Test
    fun `supported counterexample survives an unsupported sibling path`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "counterexampleBeforeUnsupported")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)

        assertTrue(outcome.unsupportedPaths.isNotEmpty())
        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = result.verdict)
        assertTrue(result.counterexamples.isNotEmpty())
        assertTrue(result.analysis.unsupportedPaths.isNotEmpty())
        assertTrue(result.counterexamples.all { it.returnValue?.jsonPrimitive?.content == "false" })
    }

    @Test
    fun `stopped search is preserved separately from unsupported semantics`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "nonterminatingProperty")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 10.milliseconds)
        val result = outcome.toCounterexampleResult(method)

        assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = outcome.stopReason)
        assertEquals(expected = "STOPPED", actual = result.analysis.stopReason)
        assertTrue(result.analysis.unsupportedPaths.isEmpty())
        assertTrue(result.analysis.resolutionErrors.isEmpty())
        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = result.verdict)
    }

    @Test
    fun `unresolved cyclic array witness is preserved in filtering responses`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "cyclicProperty", className = "CyclicWitnessFixture")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)
        val exceptionsResult = outcome.toExceptionsResult(method)
        val json = McpJson.parseToJsonElement(McpJson.encodeToString(result)).jsonObject

        assertTrue(outcome.result.isNotEmpty())
        assertTrue(outcome.unsupportedPaths.isEmpty())
        assertTrue(outcome.result.all { it.test == null && it.error != null })
        assertTrue(result.analysis.resolutionErrors.isNotEmpty())
        assertEquals(expected = result.analysis.resolutionErrors, actual = exceptionsResult.analysis.resolutionErrors)
        assertEquals(expected = outcome.result.size, actual = result.totalPathsExplored)
        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = result.verdict)
        val encodedErrors = json.getValue("analysis").jsonObject.getValue("resolutionErrors").jsonArray
        assertTrue(encodedErrors.isNotEmpty())
    }

    @Test
    fun `reachability and uncovered branches retain unsupported source diagnostics`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "unsupportedAccessor")
        val target = method.cfg.stmts.single { it is EtsReturnStmt }

        val reachability = runner.runReachability(
            scene = scene,
            method = method,
            target = target,
            timeout = 5.seconds,
        )
        val unreachable = runner.runUnreachableDetection(
            scene = scene,
            methods = listOf(method),
            timeout = 5.seconds,
        )

        assertFalse(reachability.result.reached)
        assertTrue(reachability.toDiagnosticsDto().unsupportedPaths.isNotEmpty())
        assertTrue(unreachable.toDiagnosticsDto().unsupportedPaths.isNotEmpty())
    }

    @Test
    fun `exhausted supported property retains an ordinary bounded negative`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "supportedTrue")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = outcome.stopReason)
        assertFalse(result.analysis.incomplete)
        assertEquals(expected = "NO_COUNTEREXAMPLE_WITHIN_BUDGET", actual = result.verdict)
    }

    @Test
    fun `supported false result remains a concrete counterexample`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "supportedFalse")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)

        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = result.verdict)
        assertEquals(expected = 1, actual = result.counterexamples.size)
    }

    @Test
    fun `concrete property crash is reported as a failure`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "crashingProperty")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)
        val exceptionsResult = outcome.toExceptionsResult(method)

        assertEquals(expected = "PROPERTY_CRASHED", actual = result.verdict)
        assertEquals(expected = 1, actual = result.crashes.size)
        assertEquals(expected = result.crashes, actual = exceptionsResult.exceptionalCases)
    }

    @Test
    fun `unknown calls retain incomplete diagnostics in every analysis mode`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "unknownProperty")
        val target = method.cfg.stmts.single { it is EtsReturnStmt }

        val tests = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val reachability = runner.runReachability(scene, method, target, timeout = 5.seconds)
        val unreachable = runner.runUnreachableDetection(scene, listOf(method), timeout = 5.seconds)

        val testResult = tests.toCounterexampleResult(method)
        val hasConcreteCrash = testResult.crashes.isNotEmpty()
        val expectedVerdict = if (hasConcreteCrash) "PROPERTY_CRASHED" else "INCOMPLETE_ANALYSIS"
        assertEquals(expected = expectedVerdict, actual = testResult.verdict)
        assertFalse(reachability.result.reached)
        for (outcome in listOf(tests, reachability, unreachable)) {
            val reason = outcome.unsupportedPaths.single()
            val hasExpectedCallReason = reason.contains("METHOD_BODY_UNAVAILABLE") ||
                reason.contains("INTERPROCEDURAL_ANALYSIS_DISABLED")
            assertTrue(reason.contains("flag"), reason)
            assertTrue(hasExpectedCallReason, reason)
            assertTrue(reason.contains("PATH_STOPPED"))
            assertTrue(outcome.toDiagnosticsDto().incomplete)
        }
    }

    @Test
    fun `supported counterexample survives an unknown call sibling`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "counterexampleBeforeUnknown")

        val outcome = runner.runTests(scene, method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)

        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = result.verdict)
        assertTrue(result.counterexamples.isNotEmpty())
        assertTrue(result.analysis.unsupportedPaths.any { it.contains("flag") })
    }

    @Test
    fun `nonterminal assignment produces entry inputs without a fabricated return`() = runBlocking {
        assertAssignmentWitness(methodName = "assignmentBeforeReturn")
    }

    @Test
    fun `assignment before a nonterminating loop remains reachable`() = runBlocking {
        assertAssignmentWitness(methodName = "assignmentBeforeLoop")
    }

    @Test
    fun `cyclic object encoding preserves good sibling cases and diagnostics`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "counterexampleBeforeCyclicObject")

        val outcome = runner.runTests(scene, method, timeout = 5.seconds)
        val cases = outcome.result.map { it.toTestCaseDto() }
        val result = outcome.toCounterexampleResult(method)
        val exceptions = outcome.toExceptionsResult(method)
        val json = McpJson.encodeToString(result)

        assertTrue(outcome.result.all { it.test != null })
        assertTrue(cases.all { it.kind == "SUCCESS" })
        val cyclicCase = cases.single { it.returnValue is kotlinx.serialization.json.JsonObject }
        val cyclicObject = assertNotNull(cyclicCase.returnValue).jsonObject
        val id = cyclicObject.getValue("\$id")
        val next = cyclicObject.getValue("properties").jsonObject.getValue("next").jsonObject
        assertEquals(expected = id, actual = next.getValue("\$ref"))
        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = result.verdict)
        assertTrue(result.analysis.resolutionErrors.isEmpty())
        assertEquals(expected = result.analysis.resolutionErrors, actual = exceptions.analysis.resolutionErrors)
        assertTrue(json.contains("COUNTEREXAMPLE_FOUND"))
    }

    @Test
    fun `cancelling a waiting tool propagates and leaves serialized runner usable`() = runBlocking {
        val (scene, loopingMethod) = loadMethod(methodName = "nonterminatingProperty")
        val method = MethodLookup.findMethod(scene, className = "AnalysisFixture", methodName = "supportedFalse")
        val active = async(start = CoroutineStart.UNDISPATCHED) {
            runner.runTests(scene, loopingMethod, timeout = 250.milliseconds)
        }
        var returnedFromCancelledTool = false
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            runTool {
                runner.runTests(scene, method, timeout = 5.seconds)
                textResult("completed")
            }
            returnedFromCancelledTool = true
        }

        waiting.cancelAndJoin()
        active.await()
        val subsequent = runner.runTests(scene, method, timeout = 5.seconds)

        assertTrue(waiting.isCancelled)
        assertFalse(returnedFromCancelledTool)
        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = subsequent.toCounterexampleResult(method).verdict)
    }

    private suspend fun assertAssignmentWitness(methodName: String) {
        val (scene, method) = loadMethod(methodName = methodName)
        val target = method.cfg.stmts.filterIsInstance<EtsAssignStmt>().single {
            (it.lhv as? EtsLocal)?.name == "marker"
        }

        val outcome = runner.runReachability(scene, method, target, timeout = 5.seconds)
        val witness = assertNotNull(outcome.result.witness).toTestCaseDto()

        assertTrue(outcome.result.reached)
        assertEquals(expected = "INPUTS", actual = witness.kind)
        assertEquals(expected = 1, actual = witness.parameters.size)
        assertNull(witness.returnValue)
        assertNull(witness.exception)
        assertNull(witness.resolutionError)
        assertTrue(outcome.executionErrors.isEmpty())
    }

    private fun loadMethod(methodName: String, className: String = "AnalysisFixture"): Pair<EtsScene, EtsMethod> {
        val resource = requireNotNull(javaClass.getResourceAsStream("/AnalysisOutcomes.ts"))
        val sourceText = resource.bufferedReader().use { it.readText() }
        val source = directory.resolve("AnalysisOutcomes.ts")
        source.writeText(sourceText)
        val scene = SceneCache().getScene(source.toString())
        val method = MethodLookup.findMethod(scene = scene, className = className, methodName = methodName)

        return scene to method
    }
}
