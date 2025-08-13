package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import kotlin.test.Test

class Division : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Division.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test number div number`() {
        val method = getMethod("divNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                (a eq 12) && (b eq 3) && (r eq 4)
            },
            { a, b, r ->
                (a eq 7.5) && (b eq -2.5) && (r eq -3.0)
            },
            { a, b, r ->
                // Inf/Inf = NaN
                (a.number == Double.POSITIVE_INFINITY) && (b.number == Double.POSITIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // Inf/-Inf = NaN
                (a.number == Double.POSITIVE_INFINITY) && (b.number == Double.NEGATIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // Inf/NaN = NaN
                (a.number == Double.POSITIVE_INFINITY) && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // Inf/0 = +-Infinity (depends on sign of 0)
                (a.number == Double.POSITIVE_INFINITY) && (b.number == 0.0) && r.number.isInfinite()
            },
            { a, b, r ->
                // Inf/x = Inf
                (a.number == Double.POSITIVE_INFINITY) && b.number.isFinite() && (b.number > 0) && (r.number == Double.POSITIVE_INFINITY)
            },
            { a, b, r ->
                // Inf/-x = -Inf
                (a.number == Double.POSITIVE_INFINITY) && b.number.isFinite() && (b.number < 0) && (r.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r ->
                // -Inf/Inf = NaN
                (a.number == Double.NEGATIVE_INFINITY) && (b.number == Double.POSITIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // -Inf/-Inf = NaN
                (a.number == Double.NEGATIVE_INFINITY) && (b.number == Double.NEGATIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // -Inf/NaN = NaN
                (a.number == Double.NEGATIVE_INFINITY) && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // -Inf/0 = +-Infinity (depends on sign of 0)
                (a.number == Double.NEGATIVE_INFINITY) && (b.number == 0.0) && r.number.isInfinite()
            },
            { a, b, r ->
                // -Inf/x = -Inf
                (a.number == Double.NEGATIVE_INFINITY) && b.number.isFinite() && (b.number > 0) && (r.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r ->
                // -Inf/-x = Inf
                (a.number == Double.NEGATIVE_INFINITY) && b.number.isFinite() && (b.number < 0) && (r.number == Double.POSITIVE_INFINITY)
            },
            { a, b, r ->
                // NaN/Inf = NaN
                a.isNaN() && (b.number == Double.POSITIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // NaN/-Inf = NaN
                a.isNaN() && (b.number == Double.NEGATIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // NaN/NaN = NaN
                a.isNaN() && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // NaN/0 = NaN
                a.isNaN() && (b.number == 0.0) && r.isNaN()
            },
            { a, b, r ->
                // NaN/x = NaN
                a.isNaN() && b.number.isFinite() && (b.number > 0) && r.isNaN()
            },
            { a, b, r ->
                // NaN/-x = NaN
                a.isNaN() && b.number.isFinite() && (b.number < 0) && r.isNaN()
            },
            { a, b, r ->
                // 0/Inf = 0
                (a.number == 0.0) && (b.number == Double.POSITIVE_INFINITY) && (r.number == 0.0)
            },
            { a, b, r ->
                // 0/-Inf = -0
                (a.number == 0.0) && (b.number == Double.NEGATIVE_INFINITY) && (r.number == -0.0)
            },
            { a, b, r ->
                // 0/NaN = NaN
                (a.number == 0.0) && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // 0/0 = NaN
                (a.number == 0.0) && (b.number == 0.0) && r.isNaN()
            },
            { a, b, r ->
                // 0/x = 0
                (a.number == 0.0) && b.number.isFinite() && (b.number > 0) && (r.number == 0.0)
            },
            { a, b, r ->
                // 0/-x = -0
                (a.number == 0.0) && b.number.isFinite() && (b.number < 0) && (r.number == -0.0)
            },
            { a, b, r ->
                // x/Inf = 0
                a.number.isFinite() && (a.number > 0) && (b.number == Double.POSITIVE_INFINITY) && (r.number == 0.0)
            },
            { a, b, r ->
                // x/-Inf = -0
                a.number.isFinite() && (a.number > 0) && (b.number == Double.NEGATIVE_INFINITY) && (r.number == -0.0)
            },
            { a, b, r ->
                // x/NaN = NaN
                a.number.isFinite() && (a.number > 0) && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // x/0 = +-Infinity (depends on sign of x)
                a.number.isFinite() && (a.number > 0) && (b.number == 0.0) && r.number.isInfinite()
            },
            { a, b, r ->
                // x/y = non-negative (zero, pos, inf)
                a.number.isFinite() && (a.number > 0) && b.number.isFinite() && (b.number > 0) && (r.number == a.number / b.number)
            },
            { a, b, r ->
                // x/-y = non-positive (zero, neg, -inf)
                a.number.isFinite() && (a.number < 0) && b.number.isFinite() && (b.number < 0) && (r.number == a.number / b.number)
            },
            { a, b, r ->
                // -x/Inf = -0
                a.number.isFinite() && (a.number < 0) && (b.number == Double.POSITIVE_INFINITY) && (r.number == -0.0)
            },
            { a, b, r ->
                // -x/-Inf = 0
                a.number.isFinite() && (a.number < 0) && (b.number == Double.NEGATIVE_INFINITY) && (r.number == 0.0)
            },
            { a, b, r ->
                // -x/NaN = NaN
                a.number.isFinite() && (a.number < 0) && b.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // -x/0 = +-Infinity (depends on sign of x)
                a.number.isFinite() && (a.number < 0) && (b.number == 0.0) && r.number.isInfinite()
            },
            { a, b, r ->
                // -x/y = non-positive (zero, neg, -inf)
                a.number.isFinite() && (a.number < 0) && b.number.isFinite() && (b.number > 0) && (r.number <= 0) && (r.number == a.number / b.number)
            },
            { a, b, r ->
                // -x/-y = non-negative (zero, pos, inf)
                a.number.isFinite() && (a.number < 0) && b.number.isFinite() && (b.number < 0) && (r.number >= 0) && (r.number == a.number / b.number)
            },
            invariants = arrayOf(
                { _, _, _ -> true }
            )
        )
    }

    @Test
    fun `test boolean div boolean`() {
        val method = getMethod("divBooleanAndBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (a.value && b.value) && (r.number == 1.0) },
            { a, b, r -> (a.value && !b.value) && (r.number == Double.POSITIVE_INFINITY) },
            { a, b, r -> (!a.value && b.value) && (r.number == 0.0) },
            { a, b, r -> (!a.value && !b.value) && r.isNaN() },
        )
    }

    @Test
    fun `test number div boolean`() {
        val method = getMethod("divNumberAndBoolean")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 42/true = 42
                (a.number == 42.0) && b.value && (r.number == 42.0)
            },
            { a, b, r ->
                // -5/false = -Infinity
                (a.number == -5.0) && !b.value && (r.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r ->
                // Inf/true = Inf
                (a.number == Double.POSITIVE_INFINITY) && b.value && (r.number == Double.POSITIVE_INFINITY)
            },
            { a, b, r ->
                // Inf/false = Inf
                (a.number == Double.POSITIVE_INFINITY) && !b.value && (r.number == Double.POSITIVE_INFINITY)
            },
            { a, b, r ->
                // -Inf/true = -Inf
                (a.number == Double.NEGATIVE_INFINITY) && b.value && (r.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r ->
                // -Inf/false = -Inf
                (a.number == Double.NEGATIVE_INFINITY) && !b.value && (r.number == Double.NEGATIVE_INFINITY)
            },
            { a, b, r ->
                // NaN/true = NaN
                a.isNaN() && b.value && r.isNaN()
            },
            { a, b, r ->
                // NaN/false = NaN
                a.isNaN() && !b.value && r.isNaN()
            },
            { a, b, r ->
                // 0/true = 0
                (a.number == 0.0) && b.value && (r.number == 0.0)
            },
            { a, b, r ->
                // 0/false = NaN
                (a.number == 0.0) && !b.value && r.isNaN()
            },
            { a, b, r ->
                // x/true = x
                a.number.isFinite() && (a.number > 0) && b.value && (r.number == a.number)
            },
            { a, b, r ->
                // x/false = Infinity
                a.number.isFinite() && (a.number > 0) && !b.value && (r.number == Double.POSITIVE_INFINITY)
            },
            { a, b, r ->
                // -x/true = -x
                a.number.isFinite() && (a.number < 0) && b.value && (r.number == a.number)
            },
            { a, b, r ->
                // -x/false = -Infinity
                a.number.isFinite() && (a.number < 0) && !b.value && (r.number == Double.NEGATIVE_INFINITY)
            },
            invariants = arrayOf(
                { _, _, _ -> true }
            )
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
