package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import kotlin.test.Test

class Truthy : TsMethodTestRunner() {
    private val tsPath = "/samples/Truthy.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test arrayTruthy`() {
        val method = getMethod(className, "arrayTruthy")
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
        val method = getMethod(className, "unknownFalsy")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r ->
                // null is falsy
                a is TsTestValue.TsNull && (r eq 1)
            },
            { a, r ->
                // undefined is falsy
                a is TsTestValue.TsUndefined && (r eq 2)
            },
            { a, r ->
                // false is falsy
                a is TsTestValue.TsBoolean && !a.value && (r eq 3)
            },
            { a, r ->
                // NaN is falsy
                a is TsTestValue.TsNumber && a.isNaN() && (r eq 4)
            },
            { a, r ->
                // 0 is falsy
                a is TsTestValue.TsNumber && a eq 0 && (r eq 5)
            },
            // Note: case 6 is skipped because -0 is not distinguishable from 0 in JavaScript
            { a, r ->
                // TODO: BigInt is not supported yet, so we skip case 7
                //       `a is TsTestValue.TsBigInt && (a.value == "0") && (r eq 7)`
                true
            },
            { a, r ->
                // '' (empty string) is falsy
                a is TsTestValue.TsString && a.value == "" && (r eq 8)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }
}
