package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.test.util.checkers.noResultsExpected
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllocatedArrays : TsMethodTestRunner() {
    private val tsPath = "/samples/arrays/AllocatedArrays.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test createConstantArrayOfNumbers`() {
        val method = getMethod("createConstantArrayOfNumbers")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r neq -1 }
            )
        )
    }

    @Test
    fun `test createAndReturnConstantArrayOfNumbers`() {
        val method = getMethod("createAndReturnConstantArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0) },
        )
    }

    @Test
    fun `test createAndAccessArrayOfBooleans`() {
        val method = getMethod("createAndAccessArrayOfBooleans")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r neq -1 }
            )
        )
    }

    @Test
    fun `test createArrayOfBooleans`() {
        val method = getMethod("createArrayOfBooleans")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsBoolean>>(
            method = method,
            { r -> r.values.map { it.value } == listOf(true, false, true) },
        )
    }

    @Test
    fun `test createMixedArray`() {
        val method = getMethod("createMixedArray")
        discoverProperties<TsTestValue.TsArray<*>>(
            method = method,
            { r ->
                if (r.values.size != 3) return@discoverProperties false
                val cond0 = r.values[0] is TsTestValue.TsNumber.TsDouble
                val cond1 = r.values[1] is TsTestValue.TsBoolean
                val cond2 = r.values[2] is TsTestValue.TsUndefined
                cond0 && cond1 && cond2
            },
        )
    }

    @Test
    fun `test createArrayOfUnknownValues`() {
        val method = getMethod("createArrayOfUnknownValues")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue, TsTestValue.TsArray<*>>(
            method = method,
            { a, _, _, r -> (a as TsTestValue.TsNumber).number == 1.1 && r.values[0] == a },
            { _, b, _, r -> (b as TsTestValue.TsBoolean).value && r.values[1] == b },
            { _, _, c, r -> c is TsTestValue.TsUndefined && r.values[2] == c },
            invariants = arrayOf(
                { a, b, c, r -> r.values == listOf(a, b, c) }
            )
        )
    }

    @Test
    fun `test createArrayOfNumbersAndPutDifferentTypes`() {
        val method = getMethod("createArrayOfNumbersAndPutDifferentTypes")
        checkMatches<TsTestValue.TsArray<*>>(
            method = method,
            noResultsExpected
        )
    }

    @Test
    fun `test allocatedArrayLengthExpansion`() {
        assertArrayIndexGrowthLimitation("allocatedArrayLengthExpansion")
    }

    @Test
    fun `test writeInTheIndexEqualToLength`() {
        assertArrayIndexGrowthLimitation("writeInTheIndexEqualToLength")
    }

    private fun assertArrayIndexGrowthLimitation(methodName: String) {
        val observer = RecordingObserver()
        val states = TsMachine(
            scene = scene,
            options = UMachineOptions(),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            machine.analyze(listOf(getMethod(methodName)))
        }

        assertTrue(states.isEmpty())
        assertEquals(
            TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
            observer.limitations.single().reason,
        )
    }

    private class RecordingObserver : TsInterpreterObserver {
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            limitations += event
        }
    }
}
