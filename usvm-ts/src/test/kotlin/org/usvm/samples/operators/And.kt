package org.usvm.samples.operators

import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.param
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.buildEtsMethod
import org.usvm.util.callNumberIsNaN
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.isTruthy
import org.usvm.util.neq

class And : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/And.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test boolean && boolean`() {
        val method = getMethod("andOfBooleanAndBoolean")
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
    fun `test number && number`() {

        // ```ts
        // andOfNumberAndNumber(a: number, b: number): number {
        //     const res = a && b;
        //     if (a) { // a is truthy, res is b
        //         if (b) { // b is truthy
        //             if (res === b) return 1; // res is also b
        //         } else if (Number.isNaN(b)) { // b is falsy (NaN)
        //             if (Number.isNaN(res)) return 2; // res is also NaN
        //         } else if (b === 0) { // b is falsy (0)
        //             if (res === 0) return 3; // res is also 0
        //         }
        //     } else if (Number.isNaN(a)) { // a is falsy (NaN), res is also NaN
        //         if (b) {
        //             if (Number.isNaN(res)) return 4;
        //         } else if (Number.isNaN(b)) {
        //             if (Number.isNaN(res)) return 5;
        //         } else if (b === 0) {
        //             if (Number.isNaN(res)) return 6;
        //         }
        //     } else if (a === 0) { // a is falsy (0), res is also 0
        //         if (b) {
        //             if (res === 0) return 7;
        //         } else if (Number.isNaN(b)) {
        //             if (res === 0) return 8;
        //         } else if (b === 0) {
        //             if (res === 0) return 9;
        //         }
        //     }
        //     return 0;
        // }
        // ```

        val methodName = "andOfNumberAndNumber"
        val method = buildEtsMethod(
            name = methodName,
            enclosingClass = scene.projectAndSdkClasses.single { it.name == className },
            parameters = listOf(
                "a" to EtsNumberType,
                "b" to EtsNumberType
            ),
            returnType = EtsNumberType,
        ) {
            // a := arg(0)
            val a = local("a")
            assign(a, param(0))

            // b := arg(1)
            val b = local("b")
            assign(b, param(1))

            // res := a && b
            val res = local("res")
            assign(res, and(a, b))

            // if (a) {
            ifStmt(a) {
                // if (b) {
                ifStmt(b) {
                    // if (res === b) return 1;
                    ifStmt(eqq(res, b)) {
                        ret(const(1))
                    }
                }.elseIf(callNumberIsNaN(EtsLocal("b"))) {
                    // } else if (Number.isNaN(b)) {
                    // if (Number.isNaN(res)) return 2;
                    ifStmt(callNumberIsNaN(EtsLocal("res"))) {
                        ret(const(2))
                    }
                }.elseIf(eqq(b, const(0))) {
                    // } else if (b === 0) {
                    // if (res === 0) return 3;
                    ifStmt(eqq(res, const(0))) {
                        ret(const(3))
                    }
                }
            }.elseIf(callNumberIsNaN(EtsLocal("a"))) {
                // } else if (Number.isNaN(a)) {
                // if (b) {
                ifStmt(b) {
                    // if (Number.isNaN(res)) return 4;
                    ifStmt(callNumberIsNaN(EtsLocal("res"))) {
                        ret(const(4))
                    }
                }.elseIf(callNumberIsNaN(EtsLocal("b"))) {
                    // } else if (Number.isNaN(b)) {
                    // if (Number.isNaN(res)) return 5;
                    ifStmt(callNumberIsNaN(EtsLocal("res"))) {
                        ret(const(5))
                    }
                }.elseIf(eqq(b, const(0))) {
                    // } else if (b === 0) {
                    // if (Number.isNaN(res)) return 6;
                    ifStmt(callNumberIsNaN(EtsLocal("res"))) {
                        ret(const(6))
                    }
                }
            }.elseIf(eqq(a, const(0))) {
                // } else if (a === 0) {
                // if (b) {
                ifStmt(b) {
                    // if (res === 0) return 7;
                    ifStmt(eqq(res, const(0))) {
                        ret(const(7))
                    }
                }.elseIf(callNumberIsNaN(EtsLocal("b"))) {
                    // } else if (Number.isNaN(b)) {
                    // if (res === 0) return 8;
                    ifStmt(eqq(res, const(0))) {
                        ret(const(8))
                    }
                }.elseIf(eqq(b, const(0))) {
                    // } else if (b === 0) {
                    // if (res === 0) return 9;
                    ifStmt(eqq(res, const(0))) {
                        ret(const(9))
                    }
                }
            }

            // return 0;
            ret(const(0))
        }

        // val method = getMethod(methodName)
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

    @Disabled("CFG from AA is broken")
    @Test
    fun `test boolean && number`() {
        val methodName = "andOfBooleanAndNumber"
        val method = getMethod(methodName)
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

    @Disabled("CFG from AA is broken")
    @Test
    fun `test number && boolean`() {
        val method = getMethod("andOfNumberAndBoolean")
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

    @Disabled("Does not work because objects cannot be null")
    @Test
    fun `test object && object`() {
        val method = getMethod("andOfObjectAndObject")
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

    @Disabled("CFG from AA is broken")
    @Test
    fun `test unknown && unknown`() {
        val method = getMethod("andOfUnknown")
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
