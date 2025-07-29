package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import kotlin.test.Test

class InstanceFields : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/InstanceFields.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test returnSingleField`() {
        val method = getMethod("returnSingleField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                (xa.isNaN() && r.isNaN()) || (r eq xa)
            },
        )
    }

    @Test
    fun `test dispatchOverField`() {
        val method = getMethod("dispatchOverField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                (r eq 1) && (xa eq 1)
            },
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                (r eq 2) && (xa eq 2)
            },
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                (xa neq 1) && (xa neq 2) && (r eq 100)
            },
        )
    }

    @Test
    fun `test returnSumOfTwoFields`() {
        val method = getMethod("returnSumOfTwoFields")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                val xb = x.properties["b"] as TsTestValue.TsNumber
                (xa.number + xb.number) == r.number
            }
        )
    }

    @Test
    fun `test assignField`() {
        val method = getMethod("assignField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { _, r -> r eq 10 },
        )
    }
}
