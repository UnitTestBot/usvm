package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.toDouble
import kotlin.test.Test

class Division : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "operators")

    @Test
    fun testTwoNumbersDivision() {
        val method = getMethod(className, "twoNumbersDivision")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.number / b.number == 4.0 && r.number == 4.0 },
            { a, b, r -> a.number / b.number == Double.POSITIVE_INFINITY && r.number == Double.POSITIVE_INFINITY },
            { a, b, r -> a.number / b.number == Double.NEGATIVE_INFINITY && r.number == Double.NEGATIVE_INFINITY },
            { a, b, r -> (a.number / b.number).isNaN() && r.number.isNaN() },
            { a, b, r -> a.number / b.number != 4.0 && r.number == a.number / b.number }
        )
    }

    @Test
    fun testBooleanDivision() {
        val method = getMethod(className, "booleanDivision")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.value.toDouble() / b.value.toDouble() == 0.0 && r.number == 0.0 },
            { a, b, r ->
                a.value.toDouble() / b.value.toDouble() != 0.0 && (r.number == (a.value.toDouble() / b.value.toDouble()) || r.number.isNaN())
            }
        )
    }

    @Test
    fun testMixedDivision() {
        val method = getMethod(className, "mixedDivision")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.number / b.value.toDouble() == 4.0 && r.number == 4.0 },
            { a, b, r -> a.number / b.value.toDouble() == Double.POSITIVE_INFINITY && r.number == Double.POSITIVE_INFINITY },
            { a, b, r -> a.number / b.value.toDouble() == Double.NEGATIVE_INFINITY && r.number == Double.NEGATIVE_INFINITY },
            { a, b, r -> (a.number / b.value.toDouble()).isNaN() && r.number.isNaN() },
            { a, b, r -> a.number / b.value.toDouble() != 4.0 && r.number == a.number / b.value.toDouble() }
        )
    }

    @Test
    fun testUnknownDivision() {
        val method = getMethod(className, "unknownDivision")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined) && r.number.isNaN() },
            { _, _, r -> r.number == 4.0 },
            { _, _, r -> r.number == Double.POSITIVE_INFINITY },
            { _, _, r -> r.number == Double.NEGATIVE_INFINITY },
            { _, _, r -> r.number.isNaN() },
        )
    }
}