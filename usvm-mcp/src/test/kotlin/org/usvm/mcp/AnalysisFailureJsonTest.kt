package org.usvm.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.junit.jupiter.api.Test
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.mcp.exec.AnalysisOutcome
import org.usvm.mcp.exec.ResolvedCase
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.incomplete
import org.usvm.mcp.tools.toCounterexampleResult
import org.usvm.mcp.tools.toExceptionsResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests diagnostic/result grouping independently of any particular unsupported TypeScript operation. */
class AnalysisFailureJsonTest {
    private val method = EtsMethodImpl(
        signature = EtsMethodSignature(
            enclosingClass = EtsClassSignature(name = "Property", file = EtsFileSignature.UNKNOWN),
            name = "property",
            parameters = emptyList(),
            returnType = EtsBooleanType,
        ),
    )
    private val failure = "IllegalStateException: synthetic observer failure"

    @Test
    fun `execution errors make empty results incomplete and survive JSON serialization`() {
        val outcome = outcome(cases = emptyList())

        val result = outcome.toCounterexampleResult(method)
        val exceptions = outcome.toExceptionsResult(method)
        val json = McpJson.parseToJsonElement(McpJson.encodeToString(result)).jsonObject
        val encodedErrors = json.getValue("analysis").jsonObject.getValue("executionErrors").jsonArray

        assertEquals(expected = "INCOMPLETE_ANALYSIS", actual = result.verdict)
        assertTrue(result.analysis.incomplete)
        assertTrue(result.counterexamples.isEmpty())
        assertTrue(exceptions.exceptionalCases.isEmpty())
        assertEquals(expected = listOf(failure), actual = exceptions.analysis.executionErrors)
        assertEquals(expected = listOf(JsonPrimitive(failure)), actual = encodedErrors.toList())
    }

    @Test
    fun `execution errors preserve successful counterexamples and concrete crashes`() {
        val cases = listOf(
            resolvedCase(TsTestValue.TsBoolean(value = false)),
            resolvedCase(TsTestValue.TsException.StringException(message = "property failure")),
        )
        val outcome = outcome(cases)

        val result = outcome.toCounterexampleResult(method)
        val exceptions = outcome.toExceptionsResult(method)

        assertEquals(expected = "COUNTEREXAMPLE_FOUND", actual = result.verdict)
        assertEquals(expected = 1, actual = result.counterexamples.size)
        assertEquals(expected = JsonPrimitive(false), actual = result.counterexamples.single().returnValue)
        assertEquals(expected = 1, actual = result.crashes.size)
        assertEquals(expected = result.crashes, actual = exceptions.exceptionalCases)
        assertEquals(expected = listOf(failure), actual = result.analysis.executionErrors)
        assertTrue(result.analysis.incomplete)
    }

    private fun outcome(cases: List<ResolvedCase>) = AnalysisOutcome(
        result = cases,
        stopReason = TsAnalysisStopReason.EXHAUSTED,
        unsupportedPaths = emptyList(),
        executionErrors = listOf(failure),
    )

    private fun resolvedCase(value: TsTestValue): ResolvedCase {
        val parameters = TsParametersState(thisInstance = null, parameters = emptyList(), globals = emptyMap())
        val test = TsTest(method = method, before = parameters, after = parameters, returnValue = value)

        return ResolvedCase(test = test, error = null)
    }
}
