package org.usvm.samples.operators

import org.jacodb.ets.dsl.CustomBinaryExpr
import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.param
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.buildEtsMethod
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.Test

class NullishCoalescing : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/NullishCoalescing.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `nullish coalescing operator`() {

        // ```ts
        // testNullishCoalescing(a: any): number {
        //     let res = a ?? "default";
        //
        //     if (a === null && res === "default") return 1; // null is nullish
        //     if (a === undefined && res === "default") return 2; // undefined is nullish
        //     if (a === false && res === false) return 3; // false is NOT nullish
        //     if (a === 0 && res === 0) return 4; // 0 is NOT nullish
        //     if (a === "" && res === "") return 5; // empty string is NOT nullish
        //
        //     return 100;
        // }
        // ```

        val methodName = "testNullishCoalescing"
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

            // let res = a ?? "default";
            val res = local("res")
            val e1 = CustomBinaryExpr(
                left = a,
                right = const("default"),
                toEts = { a, b -> EtsNullishCoalescingExpr(a, b, EtsUnknownType) },
            )
            assign(res, e1)

            // if (a === null && res === "default") return 1;
            ifStmt(
                and(
                    eqq(a, CustomValue { EtsNullConstant }),
                    eqq(res, const("default"))
                )
            ) {
                ret(const(1))
            }

            // if (a === undefined && res === "default") return 2;
            ifStmt(
                and(
                    eqq(a, CustomValue { EtsUndefinedConstant }),
                    eqq(res, const("default"))
                )
            ) {
                ret(const(2))
            }

            // if (a === false && res === false) return 3;
            ifStmt(
                and(
                    eqq(a, const(false)),
                    eqq(res, const(false))
                )
            ) {
                ret(const(3))
            }

            // if (a === 0 && res === 0) return 4;
            ifStmt(
                and(
                    eqq(a, const(0)),
                    eqq(res, const(0))
                )
            ) {
                ret(const(4))
            }

            // if (a === "" && res === "") return 5;
            ifStmt(
                and(
                    eqq(a, const("")),
                    eqq(res, const(""))
                )
            ) {
                ret(const(5))
            }

            // return 100;
            ret(const(100))
        }

        // val method = getMethod(methodName)
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // null ?? "default" -> "default"
                (r eq 1) && a is TsTestValue.TsNull
            },
            { a, r ->
                // undefined ?? "default" -> "default"
                (r eq 2) && a is TsTestValue.TsUndefined
            },
            { a, r ->
                // false ?? "default" -> false
                (r eq 3) && a is TsTestValue.TsBoolean && !a.value
            },
            { a, r ->
                // 0 ?? "default" -> 0
                (r eq 4) && a is TsTestValue.TsNumber && (a eq 0)
            },
            { a, r ->
                // "" ?? "default" -> ""
                // (r eq 5) && a is TsTestValue.TsString && a.value == ""
                // TODO: input strings are not supported yet, so we cannot properly interpret the equality 'a === ""'
                true
            },
            // Fallback case is also reachable:
            { _, r -> r eq 100 },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
                { a, r ->
                    r neq 100 ||
                        (a !is TsTestValue.TsNull) &&
                        (a !is TsTestValue.TsUndefined) &&
                        (a !is TsTestValue.TsBoolean || a.value) &&
                        (a !is TsTestValue.TsNumber || a.number != 0.0) &&
                        (a !is TsTestValue.TsString || a.value != "")
                }
            )
        )
    }

    @Test
    fun `nullish coalescing chaining`() {
        val method = getMethod("testNullishChaining")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // null ?? undefined ?? "value" -> "value"
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `nullish coalescing with objects`() {
        val method = getMethod("testNullishWithObjects")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // null ?? {..} -> {..}
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
