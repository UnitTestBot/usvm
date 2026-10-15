package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.usvm.ts.calls.CallsExperimentTestSupport.manifest
import org.usvm.ts.calls.CallsExperimentTestSupport.targetResult
import org.usvm.ts.pbt.model.JsConcreteValue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallsExperimentSerializationTest {
    @Test
    fun `legacy source target defaults to entry mode`() {
        val encoded = """
            {
              "targetId": "target",
              "siteId": "site",
              "sourcePath": "fixture.ts",
              "startOffset": 0,
              "endOffset": 6,
              "start": { "line": 0, "column": 0 },
              "end": { "line": 0, "column": 6 }
            }
        """.trimIndent()

        val target = CallsExperimentJson.json.decodeFromString<CallsSourceTarget>(encoded)

        assertEquals(CallsSourceTargetMode.ENTRY, target.mode)
        assertNull(target.returnExpressionStartOffset)
        assertNull(target.returnExpressionEndOffset)
    }

    @Test
    fun `completed return target preserves explicit expression offsets`() {
        val entryTarget = manifest(sourceRoot = ".", seeds = listOf(17L))
            .projects.single().functions.single().targets.single()
        val completedReturn = entryTarget.copy(
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpressionStartOffset = 7,
            returnExpressionEndOffset = 10,
        )

        val encoded = CallsExperimentJson.json.encodeToString(completedReturn)
        val decoded = CallsExperimentJson.json.decodeFromString<CallsSourceTarget>(encoded)

        assertEquals(completedReturn, decoded)
    }

    @Test
    fun `manifest rejects breadth first search`() {
        val accepted = manifest(sourceRoot = ".", seeds = listOf(17L))

        assertEquals(CALLS_PATH_SELECTION_STRATEGY.name, accepted.searchPolicy)
        val error = assertFailsWith<IllegalArgumentException> {
            accepted.copy(searchPolicy = "BFS")
        }

        assertTrue(error.message.orEmpty().contains(CALLS_PATH_SELECTION_STRATEGY.name))
    }

    @Test
    fun `target result witness uses lossless concrete value serialization`() {
        val witness = listOf(
            JsConcreteValue.number(-0.0),
            JsConcreteValue.number(Double.NaN),
            JsConcreteValue.Array(
                elements = listOf(JsConcreteValue.Undefined, JsConcreteValue.Null, JsConcreteValue.String("value")),
            ),
        )
        val result = targetResult(inputs = witness)

        val encoded = CallsExperimentJson.json.encodeToString<CallsRawRecord>(result)
        val decoded = CallsExperimentJson.json.decodeFromString<CallsRawRecord>(encoded) as CallsTargetResult

        assertEquals(result, decoded)
        assertEquals(witness, decoded.inputs)
    }
}
