package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.Test

class Cast : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Cast.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `cast any to number`() {
        val method = getMethod("castAnyToNumber")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, r -> r eq 0 },
            { _, r -> r eq 1 },
        )
    }

    @Test
    fun `cast any to boolean`() {
        val method = getMethod("castAnyToBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.value && (r eq 1) },
            { x, r -> !x.value && (r eq 2) },
            invariants = arrayOf(
                { x, r ->
                    if (x.value) {
                        r eq 1
                    } else {
                        r eq 2
                    }
                }
            )
        )
    }

    @Disabled("Input strings are not supported")
    @Test
    fun `cast any to string`() {
        val method = getMethod("castAnyToString")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, r -> r eq 1 },
            { _, r -> r eq 0 },
        )
    }

    @Test
    fun `cast number to any`() {
        val method = getMethod("castNumberToAny")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (x eq 42) && (r eq 1) },
            { x, r -> (x neq 42) && (r eq 0) },
        )
    }

    @Test
    fun `cast boolean to any`() {
        val method = getMethod("castBooleanToAny")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.value && (r eq 1) },
            { x, r -> !x.value && (r eq 2) },
            invariants = arrayOf(
                { x, r ->
                    if (x.value) {
                        r eq 1
                    } else {
                        r eq 2
                    }
                }
            )
        )
    }

    @Test
    fun `cast with multiple branches`() {
        val method = getMethod("castWithMultipleBranches")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq 0) && (x is TsTestValue.TsNumber) && (x.number <= 0) },
            { x, r -> (r eq 1) && (x is TsTestValue.TsNumber) && (x.number > 10) },
            { x, r -> (r eq 2) && (x is TsTestValue.TsNumber) && (x.number <= 10) && (x.number > 5) },
            { x, r -> (r eq 3) && (x is TsTestValue.TsNumber) && (x.number <= 5) && (x.number > 0) },
        )
    }

    @Test
    fun `cast object to interface`() {
        val method = getMethod("castObjectToInterface")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, r -> r eq 100 },
            { _, r -> r eq 0 },
        )
    }

    @Test
    fun `cast nullable to number`() {
        val method = getMethod("castNullableToNumber")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { x, r -> r eq -1 },
            { x, r -> r eq 5 },
            { x, r -> r eq 0 },
        )
    }

    @Test
    fun `cast chained`() {
        val method = getMethod("castChained")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 7 },
            { r -> r eq 0 },
        )
    }

    @Test
    fun `cast in expression`() {
        val method = getMethod("castInExpression")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 3 },
            { r -> r eq 0 },
        )
    }

    @Test
    fun `cast and arithmetic`() {
        val method = getMethod("castAndArithmetic")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { x, r -> r eq 1 },
            { x, r -> r eq 0 },
        )
    }

    @Test
    fun `cast union type`() {
        val method = getMethod("castUnionType")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 15 },
            { r -> r eq 0 },
        )
    }
}
