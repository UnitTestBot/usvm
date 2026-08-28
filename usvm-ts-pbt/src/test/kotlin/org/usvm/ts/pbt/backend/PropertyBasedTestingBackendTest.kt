package org.usvm.ts.pbt.backend

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PropertyBasedTestingBackendTest {
    @Test
    fun `configuration rejects non-positive run counts and timeouts`() {
        assertFailsWith<IllegalArgumentException> {
            PropertyRunConfiguration(numRuns = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            PropertyRunConfiguration(timeoutMillis = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            PropertyRunConfiguration(timeoutMillis = Int.MAX_VALUE.toLong() + 1)
        }
    }

    @Test
    fun `configuration has no arbitrary run or one-day timeout cap`() {
        val configuration = PropertyRunConfiguration(
            numRuns = 10_001,
            timeoutMillis = 86_400_001,
        )

        assertEquals(10_001, configuration.numRuns)
        assertEquals(86_400_001, configuration.timeoutMillis)
    }

    @Test
    fun `configuration retains tagged positional examples`() {
        val examples = listOf(
            listOf(JsConcreteValue.Boolean(true), JsConcreteValue.Undefined),
        )

        val configuration = PropertyRunConfiguration(
            seed = 42,
            replayPath = "1:0",
            numRuns = 25,
            timeoutMillis = 1_000,
            examples = examples,
        )

        assertEquals(examples, configuration.examples)
    }

    @Test
    fun `success result rejects failure-only fields`() {
        assertFailsWith<IllegalArgumentException> {
            successfulResult().copy(
                counterexample = listOf(JsConcreteValue.Boolean(false)),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            successfulResult().copy(
                failure = PropertyFailureDetails(
                    kind = PropertyFailureKind.PROPERTY,
                    errorName = "Error",
                    message = "predicate returned false",
                ),
            )
        }
    }

    @Test
    fun `failure result requires failure details`() {
        assertFailsWith<IllegalArgumentException> {
            successfulResult().copy(status = PropertyRunStatus.FAILURE)
        }
    }

    @Test
    fun `result rejects negative counters and execution time`() {
        assertFailsWith<IllegalArgumentException> {
            successfulResult().copy(numShrinks = -1)
        }

        assertFailsWith<IllegalArgumentException> {
            successfulResult().copy(executionTimeMillis = -1)
        }
    }

    private fun successfulResult() = PropertyRunResult(
        propertyId = PropertyId("example.property"),
        status = PropertyRunStatus.SUCCESS,
        seed = 42,
        replayPath = null,
        counterexample = null,
        numRuns = 100,
        numSkips = 0,
        numShrinks = 0,
        failure = null,
        executionTimeMillis = 10,
    )
}
