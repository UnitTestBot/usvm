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
                !a.value && !b.value && (r eq 1)
            },
            { a, b, r ->
                // false && true -> false
                !a.value && b.value && (r eq 2)
            },
            { a, b, r ->
                // true && false -> false
                a.value && !b.value && (r eq 3)
            },
            { a, b, r ->
                // true && true -> true
                a.value && b.value && (r eq 4)
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
                isTruthy(a) && isTruthy(b) && (r eq 1)
            },
            { a, b, r ->
                // truthy && NaN -> NaN
                isTruthy(a) && b.isNaN() && (r eq 2)
            },
            { a, b, r ->
                // truthy && 0 -> 0
                isTruthy(a) && (b eq 0) && (r eq 3)
            },
            { a, b, r ->
                // NaN && truthy -> NaN
                a.isNaN() && isTruthy(b) && (r eq 4)
            },
            { a, b, r ->
                // NaN && NaN -> NaN
                a.isNaN() && b.isNaN() && (r eq 5)
            },
            { a, b, r ->
                // NaN && 0 -> NaN
                a.isNaN() && (b eq 0) && (r eq 6)
            },
            { a, b, r ->
                // 0 && truthy -> 0
                (a eq 0) && isTruthy(b) && (r eq 7)
            },
            { a, b, r ->
                // 0 && NaN -> 0
                (a eq 0) && b.isNaN() && (r eq 8)
            },
            { a, b, r ->
                // 0 && 0 -> 0
                (a eq 0) && (b eq 0) && (r eq 9)
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
                a.value && isTruthy(b) && (r eq 1)
            },
            { a, b, r ->
                // true && NaN -> NaN
                a.value && b.isNaN() && (r eq 2)
            },
            { a, b, r ->
                // true && 0 -> 0
                a.value && (b eq 0) && (r eq 3)
            },
            { a, b, r ->
                // false && truthy -> false
                !a.value && isTruthy(b) && (r eq 4)
            },
            { a, b, r ->
                // false && NaN -> false
                !a.value && b.isNaN() && (r eq 5)
            },
            { a, b, r ->
                // false && 0 -> false
                !a.value && (b eq 0) && (r eq 6)
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
                isTruthy(a) && b.value && (r eq 1)
            },
            { a, b, r ->
                // truthy && false -> false
                isTruthy(a) && !b.value && (r eq 2)
            },
            { a, b, r ->
                // NaN && true -> NaN
                a.isNaN() && b.value && (r eq 3)
            },
            { a, b, r ->
                // NaN && false -> NaN
                a.isNaN() && !b.value && (r eq 4)
            },
            { a, b, r ->
                // 0 && true -> 0
                (a eq 0) && b.value && (r eq 5)
            },
            { a, b, r ->
                // 0 && false -> 0
                (a eq 0) && !b.value && (r eq 6)
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
                isTruthy(a) && isTruthy(b) && (r eq 1)
            },
            { a, b, r ->
                // truthy && falsy -> b
                isTruthy(a) && !isTruthy(b) && (r eq 2)
            },
            { a, b, r ->
                // falsy && truthy -> a
                !isTruthy(a) && isTruthy(b) && (r eq 3)
            },
            { a, b, r ->
                // falsy && falsy -> a
                !isTruthy(a) && !isTruthy(b) && (r eq 4)
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
