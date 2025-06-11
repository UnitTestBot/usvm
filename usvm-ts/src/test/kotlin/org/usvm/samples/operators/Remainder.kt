package org.usvm.samples.operators

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.toDouble

class Remainder : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene = loadSampleScene(className, folderPrefix = "operators")

    @Test
    @Disabled("Never ends")
    fun testTwoNumbersRemainder() {
        val method = getMethod(className, "twoNumbersRemainder")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                val res = a.number % b.number
                res != res && (a.number.isNaN() || b.number.isNaN())
            },
            { a, b, r ->
                val res = a.number % b.number
                res != res && (a.number == Double.POSITIVE_INFINITY || a.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r -> a.number == 0.0 && b.number == 0.0 && r.number.isNaN() },
            { a, b, r ->
                b.number == 0.0 && a.number != 0.0 && r.number.isNaN()
            },
            { a, b, r ->
                (b.number == Double.POSITIVE_INFINITY || b.number == Double.NEGATIVE_INFINITY) && r.number == a.number
            },
            { a, b, r -> a.number == 0.0 && r.number == a.number },
            { a, b, r -> a.number % b.number == 4.0 && r.number == 4.0 },
            { a, b, r ->
                val res = a.number % b.number
                !res.isNaN() && res != 4.0 && r.number == res
            },
        )
    }

    @Test
    fun testBooleanRemainder() {
        val method = getMethod(className, "booleanRemainder")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.value.toDouble() % b.value.toDouble() == 0.0 && r.number == 0.0 },
            { a, b, r ->
                a.value.toDouble() % b.value.toDouble() != 0.0 && (r.number == (a.value.toDouble() % b.value.toDouble()) || r.number.isNaN())
            }
        )
    }

    @Test
    @Disabled("Wrong result")
    fun testMixedRemainder() {
        val method = getMethod(className, "mixedRemainder")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.number % b.value.toDouble() == 4.0 && r.number == 4.0 },
            { a, b, r -> (a.number % b.value.toDouble()).isNaN() && r.number.isNaN() },
            { a, b, r -> a.number % b.value.toDouble() != 4.0 && r.number == a.number % b.value.toDouble() }
        )
    }

    @Test
    @Disabled("Never ends")
    fun testUnknownRemainder() {
        val method = getMethod(className, "unknownRemainder")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined) && r.number.isNaN() },
            { _, _, r -> r.number == 4.0 },
            { _, _, r -> r.number.isNaN() },
            { _, _, r -> !r.number.isNaN() && r.number != 4.0 }
        )
    }
}