package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.toFp
import kotlin.test.Test

class Add : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "operators")

    @Test
    fun `bool + bool`() {
        val method = getMethod(className, "addBoolAndBool")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> r.number == 1.0 && !a.value && !b.value },
            { a, b, r -> r.number == 2.0 && !a.value && b.value },
            { a, b, r -> r.number == 3.0 && a.value && !b.value },
            { a, b, r -> r.number == 4.0 && a.value && b.value },
            invariants = arrayOf(
                { _, _, r -> r.number != -1.0 }
            )
        )
    }

    @Test
    fun `bool + number`() {
        val method = getMethod(className, "addBoolAndNumber")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> r.number == 1.0 && a.value && b.number == -1.0 },
            { a, b, r -> r.number == 2.0 && !a.value && b.number == 0.0 },
            { a, b, r -> r.number == 3.0 && a.value && b.number == 5.0 },
            { _, b, r -> r.number.isNaN() && b.number.isNaN() },
            { a, b, r ->
                val result = a.value.toFp() + b.number
                r.number == 4.0 && result != 2.2 && !result.isNaN()
            }
        )
    }

    @Test
    fun `number + number`() {
        val method = getMethod(className, "addNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.number.isNaN() && r.number.isNaN() },
            { a, b, r -> !a.number.isNaN() && b.number.isNaN() && r.number.isNaN() },
            { a, b, r -> a.number + b.number == r.number },
            { a, b, r -> !a.number.isNaN() && !b.number.isNaN() && r.number == 0.0 },
        )
    }

    @Test
    fun `number + undefined`() {
        val method = getMethod(className, "addNumberAndUndefined")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { a, r -> r.number != -1.0 },
            )
        )
    }

    @Test
    fun `number + null`() {
        val method = getMethod(className, "addNumberAndNull")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.number.isNaN() && r.number.isNaN() },
            { a, r -> !a.number.isNaN() && r.number == a.number },
        )
    }

    @Test
    fun `add unknown values`() {
        val method = getMethod(className, "addUnknownValues")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined && r.number.isNaN() },
            { a, b, r ->
                (a is TsTestValue.TsClass
                    || b is TsTestValue.TsClass
                    || (a as? TsTestValue.TsNumber)?.number?.isNaN() == true
                    || (b as? TsTestValue.TsNumber)?.number?.isNaN() == true)
                    && r.number.isNaN()
            },
            { a, b, r -> r.number == 7.0 },
            { a, b, r -> a is TsTestValue.TsNull && b is TsTestValue.TsNull && r.number == 0.0 },
            { a, b, r -> r.number == 42.0 }
        )
    }
}
