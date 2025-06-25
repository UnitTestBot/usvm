package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class InstanceFields : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test returnSingleField`() {
        val method = getMethod(className, "returnSingleField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == r.number || (xa.number.isNaN() && r.number.isNaN())
            },
        )
    }

    @Test
    fun `test dispatchOverField`() {
        val method = getMethod(className, "dispatchOverField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == 1.0 && r.number == 1.0
            },
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == 2.0 && r.number == 2.0
            },
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number != 1.0 && xa.number != 2.0 && r.number == 100.0
            },
        )
    }

    @Test
    fun `test returnSumOfTwoFields`() {
        val method = getMethod(className, "returnSumOfTwoFields")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TsTestValue.TsNumber
                val xb = x.properties["b"] as TsTestValue.TsNumber
                xa.number + xb.number == r.number
            }
        )
    }

    @Test
    fun `test assignField`() {
        val method = getMethod(className, "assignField")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { x, r -> r.number == 10.0 },
        )
    }
}
