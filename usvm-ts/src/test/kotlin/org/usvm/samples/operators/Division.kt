package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.toDouble
import kotlin.test.Test

class Division : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Division.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testTwoNumbersDivision() {
        val method = getMethod("twoNumbersDivision")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 4) && a.number / b.number == 4.0 },
            { a, b, r -> r.number == Double.POSITIVE_INFINITY && a.number / b.number == Double.POSITIVE_INFINITY },
            { a, b, r -> r.number == Double.NEGATIVE_INFINITY && a.number / b.number == Double.NEGATIVE_INFINITY },
            { a, b, r -> r.isNaN() && (a.number / b.number).isNaN() },
            { a, b, r -> r.number == a.number / b.number && a.number / b.number != 4.0 }
        )
    }

    @Test
    fun testBooleanDivision() {
        val method = getMethod("booleanDivision")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 0) && a.value.toDouble() / b.value.toDouble() == 0.0 },
            { a, b, r ->
                (r.number == (a.value.toDouble() / b.value.toDouble()) || r.isNaN()) && a.value.toDouble() / b.value.toDouble() != 0.0
            }
        )
    }

    @Test
    fun testMixedDivision() {
        val method = getMethod("mixedDivision")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 4) && a.number / b.value.toDouble() == 4.0 },
            { a, b, r -> r.number == Double.POSITIVE_INFINITY && a.number / b.value.toDouble() == Double.POSITIVE_INFINITY },
            { a, b, r -> r.number == Double.NEGATIVE_INFINITY && a.number / b.value.toDouble() == Double.NEGATIVE_INFINITY },
            { a, b, r -> r.isNaN() && (a.number / b.value.toDouble()).isNaN() },
            { a, b, r -> r.number == a.number / b.value.toDouble() && a.number / b.value.toDouble() != 4.0 }
        )
    }

    @Disabled("Long running test")
    @Test
    fun testUnknownDivision() {
        val method = getMethod("unknownDivision")
        withOptions(options.copy(useSoftConstraints = false)) {
            discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
                method = method,
                { a, b, r -> r.isNaN() && (a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined) },
                { _, _, r -> r eq 4 },
                { _, _, r -> r.number == Double.POSITIVE_INFINITY },
                { _, _, r -> r.number == Double.NEGATIVE_INFINITY },
                { _, _, r -> r.isNaN() },
            )
        }
    }
}
