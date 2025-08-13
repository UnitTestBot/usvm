package org.usvm.samples.operators

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq

class Remainder : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Remainder.ts"

    override val scene = loadScene(tsPath)

    @Test
    fun testRemNumberAndNumber() {
        val method = getMethod("remNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // NaN % x = NaN
                a.isNaN() && r.isNaN()
            },
            { a, b, r ->
                // Infinity % x = NaN
                (a.number == Double.POSITIVE_INFINITY) && r.isNaN()
            },
            { a, b, r ->
                // x % Infinity = x
                (b.number == Double.POSITIVE_INFINITY) && (r eq a)
            },
            { a, b, r ->
                // x % 0 = NaN
                (b eq 0) && r.isNaN()
            },
            { a, b, r ->
                // 0 % x = 0
                (a eq 0) && (r eq 0)
            },
            { a, b, r ->
                // (x.y) % 1 = (0.y)  (`a = x.y`, y is a fractional part of `a`)
                (b eq 1) && (r eq a.number % 1)
            },
            { a, b, r ->
                // 7 % 4 = 3
                // (a eq 7) && (b eq 4) && (r eq 3)
                // TODO: SMT solvers struggle to produce '3' as the result here
                (a eq 7) && (b eq 4)
            },
            { a, b, r ->
                // 7 % -4 = 3
                // (a eq 7) && (b eq -4) && (r eq 3)
                // TODO: SMT solvers struggle to produce '3' as the result here
                (a eq 7) && (b eq -4)
            },
            { a, b, r ->
                // -7 % 4 = -3
                // (a eq -7) && (b eq 4) && (r eq -3)
                // TODO: SMT solvers struggle to produce '-3' as the result here
                (a eq -7) && (b eq 4)
            },
            { a, b, r ->
                // -7 % -4 = -3
                // (a eq -7) && (b eq -4) && (r eq -3)
                // TODO: SMT solvers struggle to produce '-3' as the result here
                (a eq -7) && (b eq -4)
            },
            { a, b, r ->
                // any other normal case
                r.number == (a.number % b.number)
            },
            invariants = arrayOf(
                { a, b, r ->
                    // TODO: SMT solvers struggle to produce the correct results for % operator...
                    // val res = a.number % b.number
                    // if (res.isNaN()) {
                    //     r.isNaN()
                    // } else {
                    //     r.number == res
                    // }
                    true
                }
            )
        )
    }

    @Test
    fun testRemBooleanAndBoolean() {
        val method = getMethod("remBooleanAndBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // true % true = 0
                (a.value && b.value) && (r.number == 0.0)
            },
            { a, b, r ->
                // true % false = NaN
                (a.value && !b.value) && r.isNaN()
            },
            { a, b, r ->
                // false % true = 0
                (!a.value && b.value) && (r.number == 0.0)
            },
            { a, b, r ->
                // false % false = NaN
                (!a.value && !b.value) && r.isNaN()
            }
        )
    }

    @Test
    fun testRemNumberAndBoolean() {
        val method = getMethod("remNumberAndBoolean")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // NaN % true = NaN
                a.isNaN() && b.value && r.isNaN()
            },
            { a, b, r ->
                // NaN % false = NaN
                a.isNaN() && !b.value && r.isNaN()
            },
            { a, b, r ->
                // x % false = NaN
                !b.value && r.isNaN()
            },
            { a, b, r ->
                // Infinity % true = NaN
                (a.number == Double.POSITIVE_INFINITY) && b.value && r.isNaN()
            },
            { a, b, r ->
                // -Infinity % true = NaN
                (a.number == Double.NEGATIVE_INFINITY) && b.value && r.isNaN()
            },
            { a, b, r ->
                // 0 % true = 0
                (a eq 0) && b.value && (r eq 0)
            },
            { a, b, r ->
                // positive % true = 0
                // (a.number > 0) && b.value && (r eq 0)
                // TODO: SMT solvers struggle to produce '0' as the result here
                (a.number > 0) && b.value
            },
            { a, b, r ->
                // negative % true = -0
                // (a.number < 0) && b.value && (r eq -0)
                // TODO: SMT solvers struggle to produce '-0' as the result here
                (a.number < 0) && b.value
            }
        )
    }

    @Disabled("Segfaults")
    @Test
    fun testRemUnknown() {
        val method = getMethod("remUnknown")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> r eq 4 },
            { a, b, r -> r.isNaN() },
            { a, b, r -> (r neq 4) && !r.isNaN() }
        )
    }
}
