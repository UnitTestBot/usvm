package org.usvm.samples.lang

import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.param
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUndefinedConstant
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.buildEtsMethod
import org.usvm.util.callNumberIsNaN
import org.usvm.util.eq
import org.usvm.util.isNaN
import kotlin.test.Test

class Truthy : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Truthy.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test arrayTruthy`() {
        val method = getMethod("arrayTruthy")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `test unknownFalsy`() {

        // ```ts
        // unknownFalsy(a: unknown): number {
        //     if (a) return 100; // a is truthy
        //
        //     // a is falsy due to being null, undefined, false, NaN, 0, -0, 0n, or ''
        //     if (a === null) return 1;
        //     if (a === undefined) return 2;
        //     if (a === false) return 3;
        //     if (Number.isNaN(a)) return 4;
        //     if (a === 0) return 5;
        //     // if (a === -0) return 6; // -0 is not distinguishable from 0 in JavaScript
        //     // if (a === 0n) return 7; // TODO: support bigint
        //     if (a === '') return 8;
        //
        //     return 0; // unreachable
        // }
        // ```

        val methodName = "unknownFalsy"
        val method = buildEtsMethod(
            name = methodName,
            enclosingClass = scene.projectAndSdkClasses.single { it.name == className },
            parameters = listOf(
                "a" to EtsAnyType,
            ),
            returnType = EtsNumberType,
        ) {
            // a := arg(0)
            val a = local("a")
            assign(a, param(0))

            // if (a) return 100;
            ifStmt(a) {
                ret(const(100))
            }

            // if (a === null) return 1;
            ifStmt(eqq(a, CustomValue(EtsNullConstant))) {
                ret(const(1))
            }

            // if (a === undefined) return 2;
            ifStmt(eqq(a, CustomValue(EtsUndefinedConstant))) {
                ret(const(2))
            }

            // if (a === false) return 3;
            ifStmt(eqq(a, const(false))) {
                ret(const(3))
            }

            // if (Number.isNaN(a)) return 4;
            ifStmt(callNumberIsNaN(EtsLocal("a"))) {
                ret(const(4))
            }

            // if (a === 0) return 5;
            ifStmt(eqq(a, const(0))) {
                ret(const(5))
            }

            // Note: cases 6 and 7 are skipped

            // if (a === '') return 8;
            ifStmt(eqq(a, const(""))) {
                ret(const(8))
            }

            // return 0;
            ret(const(0))
        }

        // val method = getMethod(methodName)
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r ->
                // null is falsy
                (r eq 1) && a is TsTestValue.TsNull
            },
            { a, r ->
                // undefined is falsy
                (r eq 2) && a is TsTestValue.TsUndefined
            },
            { a, r ->
                // false is falsy
                (r eq 3) && a is TsTestValue.TsBoolean && !a.value
            },
            { a, r ->
                // NaN is falsy
                (r eq 4) && a is TsTestValue.TsNumber && a.isNaN()
            },
            { a, r ->
                // 0 is falsy
                (r eq 5) && a is TsTestValue.TsNumber && a eq 0
            },
            // Note: case 6 is skipped because -0 is not distinguishable from 0 in JavaScript
            { a, r ->
                // TODO: BigInt is not supported yet, so we skip case 7
                //       `a is TsTestValue.TsBigInt && (a.value == "0") && (r eq 7)`
                true
            },
            { a, r ->
                // TODO: input strings are not supported yet, so we skip case 8
                // '' (empty string) is falsy
                // (r eq 8) && a is TsTestValue.TsString && a.value == ""
                true
            },
            { a, r ->
                // TODO: currently, we cannot express `isTruthy(any)` here,
                //  but still we can expect the case 100 to be covered by some execution.
                //  `(r eq 100) && isTruthy(a)`
                (r eq 100)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }
}
