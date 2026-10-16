package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeLimitationDslTest {
    private val runner = DslPropertyRunner(
        sourceRoots = listOf(testResourcesRoot().resolve("properties/dsl")),
    )
    private val configuration = PropertyRunConfiguration(seed = 42, numRuns = 10, timeoutMillis = 10_000)

    @Test
    fun `sparse indexed growth reports its original source instead of unexplained exhaustion`() {
        val program = defineProperty(id = "runtime.sparse-write") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "sparseWrite")
            val index by int(min = 2, max = 2)

            check(name = "read-back") { write(index) equalTo 123 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        assertEquals(PropertyRunStatus.SUCCESS, run.concrete?.status)
        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, symbolic.status)
        assertNull(symbolic.inputs)
        val limitation = symbolic.diagnostics.single { it.code == "usvm.runtime.array-index-growth" }
        assertTrue(limitation.message.contains("ARRAY_INDEX_GROWTH"))
        assertTrue(limitation.message.contains("array index write would grow beyond the current length"))
        assertTrue(limitation.message.contains("statement:"))
        assertTrue(limitation.path.contains("runtime-limitations.ts:3:"), limitation.path)
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    @Test
    fun `supported counterexample and its native replay survive an excluded path`() {
        val program = defineProperty(id = "runtime.mixed-counterexample") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "supportedOrSparse")
            val index by int(min = 0, max = 2)

            check(name = "zero") { write(index) equalTo 0 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.USVM, configuration = configuration)

        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, symbolic.status)
        assertEquals(listOf(JsConcreteValue.number(0.0)), symbolic.inputs)
        assertTrue(symbolic.diagnostics.any { it.code == "usvm.runtime.array-index-growth" })
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, run.symbolicReplay?.status)
        assertEquals("zero", run.symbolicReplay?.originalReplay?.failure?.checkId)
        assertFailsWith<DslPropertyAssertionError> { run.requireSuccess() }
    }

    @Test
    fun `supported successful paths do not hide excluded model paths`() {
        val program = defineProperty(id = "runtime.mixed-no-violation") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "supportedOrSparse")
            val index by int(min = 0, max = 2)

            check(name = "positive") { write(index) greaterThan 0 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.USVM, configuration = configuration)

        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, symbolic.status)
        assertNull(symbolic.inputs)
        assertTrue(symbolic.diagnostics.any { it.code == "usvm.runtime.array-index-growth" })
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }
}
