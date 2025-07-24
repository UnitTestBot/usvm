package org.usvm.samples.operators

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import org.usvm.util.toDouble

class Remainder : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Remainder.ts"

    override val scene = loadScene(tsPath)

    @Test
    @Disabled("Never ends")
    fun testTwoNumbersRemainder() {
        val method = getMethod("twoNumbersRemainder")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                val res = a.number % b.number
                res != res && (a.isNaN() || b.isNaN())
            },
            { a, b, r ->
                val res = a.number % b.number
                res != res && (a.number == Double.POSITIVE_INFINITY || a.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r -> r.isNaN() && a.number == 0.0 && b.number == 0.0 },
            { a, b, r ->
                r.isNaN() && b.number == 0.0 && a.number != 0.0
            },
            { a, b, r ->
                (r eq a) && (b.number == Double.POSITIVE_INFINITY || b.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r -> (r eq a) && a.number == 0.0 },
            { a, b, r -> (r eq 4) && a.number % b.number == 4.0 },
            { a, b, r ->
                val res = a.number % b.number
                (r.number == res) && !res.isNaN() && res != 4.0
            },
        )
    }

    @Test
    fun testBooleanRemainder() {
        val method = getMethod("booleanRemainder")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 0) && a.value.toDouble() % b.value.toDouble() == 0.0 },
            { a, b, r ->
                (r.number == (a.value.toDouble() % b.value.toDouble()) || r.isNaN()) && a.value.toDouble() % b.value.toDouble() != 0.0
            }
        )
    }

    @Test
    @Disabled("Wrong result")
    fun testMixedRemainder() {
        val method = getMethod("mixedRemainder")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 4) && a.number % b.value.toDouble() == 4.0 },
            { a, b, r -> r.isNaN() && (a.number % b.value.toDouble()).isNaN() },
            { a, b, r -> (r.number == a.number % b.value.toDouble()) && a.number % b.value.toDouble() != 4.0 }
        )
    }

    @Test
    @Disabled("Never ends")
    fun testUnknownRemainder() {
        val method = getMethod("unknownRemainder")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> r.isNaN() && (a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined) },
            { _, _, r -> r eq 4 },
            { _, _, r -> r.isNaN() },
            { _, _, r -> (r neq 4) && !r.isNaN() }
        )
    }
}
