package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.isTruthy
import org.usvm.util.neq

class And : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/And.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test andOfBooleanAndBoolean`() {
        val method = getMethod(className, "andOfBooleanAndBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // false && false -> false
                (r eq 1) && !a.value && !b.value
            },
            { a, b, r ->
                // false && true -> false
                (r eq 2) && !a.value && b.value
            },
            { a, b, r ->
                // true && false -> false
                (r eq 3) && a.value && !b.value
            },
            { a, b, r ->
                // true && true -> true
                (r eq 4) && a.value && b.value
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }

    @Test
    fun `test andOfNumberAndNumber`() {
        val method = getMethod(className, "andOfNumberAndNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // truthy && truthy -> b
                (r eq 1) && isTruthy(a) && isTruthy(b)
            },
            { a, b, r ->
                // truthy && NaN -> NaN
                (r eq 2) && isTruthy(a) && b.isNaN()
            },
            { a, b, r ->
                // truthy && 0 -> 0
                (r eq 3) && isTruthy(a) && (b eq 0)
            },
            { a, b, r ->
                // NaN && truthy -> NaN
                (r eq 4) && a.isNaN() && isTruthy(b)
            },
            { a, b, r ->
                // NaN && NaN -> NaN
                (r eq 5) && a.isNaN() && b.isNaN()
            },
            { a, b, r ->
                // NaN && 0 -> NaN
                (r eq 6) && a.isNaN() && (b eq 0)
            },
            { a, b, r ->
                // 0 && truthy -> 0
                (r eq 7) && (a eq 0) && isTruthy(b)
            },
            { a, b, r ->
                // 0 && NaN -> 0
                (r eq 8) && (a eq 0) && b.isNaN()
            },
            { a, b, r ->
                // 0 && 0 -> 0
                (r eq 9) && (a eq 0) && (b eq 0)
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }

    @Test
    fun `test andOfBooleanAndNumber`() {
        val method = getMethod(className, "andOfBooleanAndNumber")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // true && truthy -> b
                (r eq 1) && a.value && isTruthy(b)
            },
            { a, b, r ->
                // true && NaN -> NaN
                (r eq 2) && a.value && b.isNaN()
            },
            { a, b, r ->
                // true && 0 -> 0
                (r eq 3) && a.value && (b eq 0)
            },
            { a, b, r ->
                // false && truthy -> false
                (r eq 4) && !a.value && isTruthy(b)
            },
            { a, b, r ->
                // false && NaN -> false
                (r eq 5) && !a.value && b.isNaN()
            },
            { a, b, r ->
                // false && 0 -> false
                (r eq 6) && !a.value && (b eq 0)
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }

    @Test
    fun `test andOfNumberAndBoolean`() {
        val method = getMethod(className, "andOfNumberAndBoolean")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // truthy && true -> true
                (r eq 1) && isTruthy(a) && b.value
            },
            { a, b, r ->
                // truthy && false -> false
                (r eq 2) && isTruthy(a) && !b.value
            },
            { a, b, r ->
                // NaN && true -> NaN
                (r eq 3) && a.isNaN() && b.value
            },
            { a, b, r ->
                // NaN && false -> NaN
                (r eq 4) && a.isNaN() && !b.value
            },
            { a, b, r ->
                // 0 && true -> 0
                (r eq 5) && (a eq 0) && b.value
            },
            { a, b, r ->
                // 0 && false -> 0
                (r eq 6) && (a eq 0) && !b.value
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }

    @Test
    @Disabled("Does not work because objects cannot be null")
    fun `test andOfObjectAndObject`() {
        val method = getMethod(className, "andOfObjectAndObject")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // truthy && truthy -> b
                (r eq 1) && isTruthy(a) && isTruthy(b)
            },
            { a, b, r ->
                // truthy && falsy -> b
                (r eq 2) && isTruthy(a) && !isTruthy(b)
            },
            { a, b, r ->
                // falsy && truthy -> a
                (r eq 3) && !isTruthy(a) && isTruthy(b)
            },
            { a, b, r ->
                // falsy && falsy -> a
                (r eq 4) && !isTruthy(a) && !isTruthy(b)
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }

    @Test
    fun `test andOfUnknown`() {
        val method = getMethod(className, "andOfUnknown")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // a is truthy && b is truthy
                // isTruthy(a) && isTruthy(b) && (r eq 1)
                r eq 1
            },
            { a, b, r ->
                // a is truthy && b is NaN
                // isTruthy(a) && b.isNaN() && (r eq 2)
                r eq 2
            },
            { a, b, r ->
                // a is truthy && b is 0
                // isTruthy(a) && (b eq 0) && (r eq 3)
                r eq 3
            },
            { a, b, r ->
                // a is truthy && b is false
                // isTruthy(a) && (b is TsTestValue.TsBoolean && !b.value) && (r eq 4)
                r eq 4
            },
            { a, b, r ->
                // a is NaN && b is truthy
                // a.isNaN() && isTruthy(b) && (r eq 11)
                r eq 11
            },
            { a, b, r ->
                // a is NaN && b is NaN
                // a.isNaN() && b.isNaN() && (r eq 12)
                r eq 12
            },
            { a, b, r ->
                // a is NaN && b is 0
                // a.isNaN() && (b eq 0) && (r eq 13)
                r eq 13
            },
            { a, b, r ->
                // a is NaN && b is false
                // a.isNaN() && (b is TsTestValue.TsBoolean && !b.value) && (r eq 14)
                r eq 14
            },
            { a, b, r ->
                // a is 0 && b is truthy
                // (a eq 0) && isTruthy(b) && (r eq 21)
                r eq 21
            },
            { a, b, r ->
                // a is 0 && b is NaN
                // (a eq 0) && b.isNaN() && (r eq 22)
                r eq 22
            },
            { a, b, r ->
                // a is 0 && b is 0
                // (a eq 0) && (b eq 0) && (r eq 23)
                r eq 23
            },
            { a, b, r ->
                // a is 0 && b is false
                // (a eq 0) && (b is TsTestValue.TsBoolean && !b.value) && (r eq 24)
                r eq 24
            },
            invariants = arrayOf(
                { _, _, r -> r neq 0 }
            )
        )
    }
}
