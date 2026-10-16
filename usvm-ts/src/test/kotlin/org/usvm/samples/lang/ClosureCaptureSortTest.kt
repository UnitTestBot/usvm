package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class ClosureCaptureSortTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/ClosureCaptureSort.ts")

    @Test
    fun `captures a number read from a symbolic receiver field`() {
        val method = getMethod(methodName = "numberFromInputField", className = "ClosureCaptureSort")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 0 },
        )
    }

    @Test
    fun `captures a boxed number in a number binding`() {
        val method = getMethod(methodName = "numberFromAny", className = "ClosureCaptureSort")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 42 },
            invariants = arrayOf({ result -> result eq 42 }),
        )
    }

    @Test
    fun `captures a boxed boolean in a boolean binding`() {
        val method = getMethod(methodName = "booleanFromAny", className = "ClosureCaptureSort")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `captures a boxed reference in a reference binding`() {
        val method = getMethod(methodName = "referenceFromAny", className = "ClosureCaptureSort")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 42 },
            invariants = arrayOf({ result -> result eq 42 }),
        )
    }
}
