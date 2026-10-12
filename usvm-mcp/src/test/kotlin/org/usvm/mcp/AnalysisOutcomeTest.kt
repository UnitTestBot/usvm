package org.usvm.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.SceneCache
import org.usvm.mcp.tools.toCounterexampleResult
import org.usvm.mcp.tools.toExceptionsResult
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
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
        val (scene, method) = loadMethod(methodName = "supportedTrue")

        val outcome = runner.runTests(scene = scene, method = method, timeout = Duration.ZERO)
        val result = outcome.toCounterexampleResult(method)

        assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = outcome.stopReason)
        assertEquals(expected = "STOPPED", actual = result.analysis.stopReason)
        assertTrue(result.analysis.unsupportedPaths.isEmpty())
        assertTrue(result.analysis.resolutionErrors.isEmpty())
        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = result.verdict)
    }

    @Test
    fun `unresolved callable witness is preserved in filtering responses`() = runBlocking {
        val (scene, method) = loadMethod(methodName = "callbackValue")

        val outcome = runner.runTests(scene = scene, method = method, timeout = 5.seconds)
        val result = outcome.toCounterexampleResult(method)
        val exceptionsResult = outcome.toExceptionsResult(method)
        val json = McpJson.parseToJsonElement(McpJson.encodeToString(result)).jsonObject

        assertTrue(outcome.result.isNotEmpty())
        assertTrue(outcome.result.all { it.test == null && it.error != null })
        assertTrue(result.analysis.resolutionErrors.isNotEmpty())
        assertEquals(expected = result.analysis.resolutionErrors, actual = exceptionsResult.analysis.resolutionErrors)
        assertEquals(expected = outcome.result.size, actual = result.totalPathsExplored)
        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = result.verdict)
        assertTrue(json.getValue("analysis").jsonObject.getValue("resolutionErrors").jsonArray.isNotEmpty())
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

    private fun loadMethod(methodName: String): Pair<EtsScene, EtsMethod> {
        val resource = requireNotNull(javaClass.getResourceAsStream("/AnalysisOutcomes.ts"))
        val sourceText = resource.bufferedReader().use { it.readText() }
        val source = directory.resolve("AnalysisOutcomes.ts")
        source.writeText(sourceText)
        val scene = SceneCache().getScene(source.toString())
        val method = MethodLookup.findMethod(scene = scene, className = "AnalysisFixture", methodName = methodName)

        return scene to method
    }
}
