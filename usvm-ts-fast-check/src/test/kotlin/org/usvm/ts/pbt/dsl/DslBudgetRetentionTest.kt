package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.testResourcesRoot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DslBudgetRetentionTest {
    @Test
    fun `a confirmed concrete violation avoids symbolic preparation and retains its original check`() {
        val program = defineProperty(id = "budget.concrete-violation") {
            val add = function<(Double, Double) -> Double>(module = "calculator.ts", exportName = "brokenAdd")
            val a by int(min = 0, max = 0)
            val b by int(min = 0, max = 0)

            check(name = "addition") { add(a, b) equalTo (a + b) }
        }
        val runner = DslPropertyRunner(sourceRoots = listOf(testResourcesRoot().resolve("properties/dsl")))
        val configuration = PropertyRunConfiguration(
            seed = 353,
            numRuns = 1,
            timeoutMillis = 60_000,
            examples = listOf(listOf(JsConcreteValue.number(0.0), JsConcreteValue.number(0.0))),
        )

        val result = runner.run(program, PropertyEngine.BOTH, configuration)

        assertEquals(PropertyFailureKind.PROPERTY, result.concrete?.failure?.kind)
        assertEquals("addition", result.concrete?.failure?.checkId)
        assertFalse(result.symbolicSkippedDueToDeadline)
        assertNull(result.symbolic)
        assertNull(result.symbolicReplay)
        assertFailsWith<DslPropertyAssertionError> { result.requireSuccess() }
    }
}
