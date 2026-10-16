package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
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
    private val capacity = TsOptions().maxArraySize

    @Test
    fun `array capacity reports its original source instead of unexplained exhaustion`() {
        val program = defineProperty(id = "runtime.capacity-write") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "sparseWrite")
            val index by int(min = capacity, max = capacity)

            check(name = "read-back") { write(index) equalTo 123 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        assertEquals(PropertyRunStatus.SUCCESS, run.concrete?.status)
        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, symbolic.status)
        assertNull(symbolic.inputs)
        val limitation = symbolic.diagnostics.single { it.code == "usvm.runtime.array-length-capacity" }
        assertTrue(limitation.message.contains("ARRAY_LENGTH_CAPACITY"))
        assertTrue(limitation.message.contains("array index write exceeds configured length bound"))
        assertTrue(limitation.message.contains("statement:"))
        assertTrue(limitation.path.contains("runtime-limitations.ts:3:"), limitation.path)
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    @Test
    fun `supported counterexample and its native replay survive an excluded path`() {
        val program = defineProperty(id = "runtime.mixed-counterexample") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "supportedOrSparse")
            val index by int(min = 0, max = capacity)

            assume { (index equalTo 0) or (index equalTo capacity) }

            check(name = "zero") { write(index) equalTo 0 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.USVM, configuration = configuration)

        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, symbolic.status)
        assertEquals(listOf(JsConcreteValue.number(0.0)), symbolic.inputs)
        assertTrue(symbolic.diagnostics.any { it.code == "usvm.runtime.array-length-capacity" })
        assertEquals(PropertyRunStatus.FAILURE, run.symbolicReplay?.status)
        assertEquals("zero", run.symbolicReplay?.failure?.checkId)
        assertFailsWith<DslPropertyAssertionError> { run.requireSuccess() }
    }

    @Test
    fun `supported successful paths do not hide excluded model paths`() {
        val program = defineProperty(id = "runtime.mixed-no-violation") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "supportedOrSparse")
            val index by int(min = 0, max = capacity)

            assume { (index equalTo 0) or (index equalTo capacity) }

            check(name = "positive") { write(index) greaterThan 0 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.USVM, configuration = configuration)

        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, symbolic.status)
        assertNull(symbolic.inputs)
        assertTrue(symbolic.diagnostics.any { it.code == "usvm.runtime.array-length-capacity" })
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    @Test
    fun `small sparse cache write now executes symbolically and agrees with native TypeScript`() {
        val program = defineProperty(id = "runtime.supported-sparse-write") {
            val write = function<(Double) -> Double>(module = "runtime-limitations.ts", exportName = "sparseWrite")
            val index by int(min = 2, max = 2)

            check(name = "read-back") { write(index) equalTo 123 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        run.requireSuccess()
        assertEquals(PropertyRunStatus.SUCCESS, run.concrete?.status)
        val symbolic = assertNotNull(run.symbolic)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, symbolic.status)
        assertTrue(symbolic.diagnostics.none { it.code.startsWith("usvm.runtime.") })
    }
}
