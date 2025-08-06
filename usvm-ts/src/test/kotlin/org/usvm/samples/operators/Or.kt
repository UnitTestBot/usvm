package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.isTruthy

class Or : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Or.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test orOfBooleanAndBoolean`() {
        val method = getMethod("orOfBooleanAndBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // true || true -> true
                (r eq 1) && a.value && b.value
            },
            { a, b, r ->
                // true || false -> true
                (r eq 2) && a.value && !b.value
            },
            { a, b, r ->
                // false || true -> true
                (r eq 3) && !a.value && b.value
            },
            { a, b, r ->
                // false || false -> false
                (r eq 4) && !a.value && !b.value
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `test orOfNumberAndNumber`() {
        val method = getMethod("orOfNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, _, r ->
                // truthy || any -> a
                (r eq 1) && isTruthy(a)
            },
            { a, b, r ->
                // NaN || truthy -> b
                (r eq 2) && a.isNaN() && isTruthy(b)
            },
            { a, b, r ->
                // NaN || NaN -> NaN
                (r eq 3) && a.isNaN() && b.isNaN()
            },
            { a, b, r ->
                // NaN || 0 -> 0
                (r eq 4) && a.isNaN() && (b eq 0)
            },
            { a, b, r ->
                // 0 || truthy -> b
                (r eq 5) && (a eq 0) && isTruthy(b)
            },
            { a, b, r ->
                // 0 || NaN -> NaN
                (r eq 6) && (a eq 0) && b.isNaN()
            },
            { a, b, r ->
                // 0 || 0 -> 0
                (r eq 7) && (a eq 0) && (b eq 0)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 }
            )
        )
    }
}
