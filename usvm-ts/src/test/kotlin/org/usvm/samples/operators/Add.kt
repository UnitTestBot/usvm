package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import org.usvm.util.toDouble
import kotlin.test.Test

class Add : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Add.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `bool + bool`() {
        val method = getMethod("addBoolAndBool")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 1) && !a.value && !b.value },
            { a, b, r -> (r eq 2) && !a.value && b.value },
            { a, b, r -> (r eq 3) && a.value && !b.value },
            { a, b, r -> (r eq 4) && a.value && b.value },
            invariants = arrayOf(
                { _, _, r -> r neq -1 }
            )
        )
    }

    @Test
    fun `bool + number`() {
        val method = getMethod("addBoolAndNumber")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (r eq 1) && a.value && (b eq -1) },
            { a, b, r -> (r eq 2) && !a.value && (b eq 0) },
            { a, b, r -> (r eq 3) && a.value && (b eq 5) },
            { _, b, r -> r.isNaN() && b.isNaN() },
            { a, b, r ->
                val result = a.value.toDouble() + b.number
                (r eq 4) && result != 2.2 && !result.isNaN()
            }
        )
    }

    @Test
    fun `number + number`() {
        val method = getMethod("addNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a.isNaN() && r.isNaN() },
            { a, b, r -> !a.isNaN() && b.isNaN() && r.isNaN() },
            { a, b, r -> a.number + b.number == r.number },
            { a, b, r -> !a.isNaN() && !b.isNaN() && (r eq 0) },
        )
    }

    @Test
    fun `number + undefined`() {
        val method = getMethod("addNumberAndUndefined")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { _, r -> r neq -1 },
            )
        )
    }

    @Test
    fun `number + null`() {
        val method = getMethod("addNumberAndNull")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.isNaN() && r.isNaN() },
            { a, r -> !a.isNaN() && (r eq a) },
        )
    }

    @Disabled("Flaky test, see https://github.com/UnitTestBot/usvm/issues/310")
    @Test
    fun `add unknown values`() {
        val method = getMethod("addUnknownValues")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> a is TsTestValue.TsUndefined || b is TsTestValue.TsUndefined && r.isNaN() },
            // This condition sometimes fails, in case `bool` + `null`
            // TODO https://github.com/UnitTestBot/usvm/issues/310
            { a, b, r ->
                (a is TsTestValue.TsClass
                    || b is TsTestValue.TsClass
                    || (a as? TsTestValue.TsNumber)?.number?.isNaN() == true
                    || (b as? TsTestValue.TsNumber)?.number?.isNaN() == true)
                    && r.isNaN()
            },
            { a, b, r -> r eq 7 },
            { a, b, r -> a is TsTestValue.TsNull && b is TsTestValue.TsNull && (r eq 0) },
            { a, b, r -> r eq 42 }
        )
    }
}
