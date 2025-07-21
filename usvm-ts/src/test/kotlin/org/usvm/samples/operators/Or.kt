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
        val method = getMethod(className, "orOfBooleanAndBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // true || true -> true
                a.value && b.value && (r eq 1)
            },
            { a, b, r ->
                // true || false -> true
                a.value && !b.value && (r eq 2)
            },
            { a, b, r ->
                // false || true -> true
                !a.value && b.value && (r eq 3)
            },
            { a, b, r ->
                // false || false -> false
                !a.value && !b.value && (r eq 4)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `test orOfNumberAndNumber`() {
        val method = getMethod(className, "orOfNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, _, r ->
                // truthy || any -> a
                isTruthy(a) && (r eq 1)
            },
            { a, b, r ->
                // NaN || truthy -> b
                a.isNaN() && isTruthy(b) && (r eq 2)
            },
            { a, b, r ->
                // NaN || NaN -> NaN
                a.isNaN() && b.isNaN() && (r eq 3)
            },
            { a, b, r ->
                // NaN || 0 -> 0
                a.isNaN() && (b eq 0) && (r eq 4)
            },
            { a, b, r ->
                // 0 || truthy -> b
                (a eq 0) && isTruthy(b) && (r eq 5)
            },
            { a, b, r ->
                // 0 || NaN -> NaN
                (a eq 0) && b.isNaN() && (r eq 6)
            },
            { a, b, r ->
                // 0 || 0 -> 0
                (a eq 0) && (b eq 0) && (r eq 7)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 }
            )
        )
    }
}
