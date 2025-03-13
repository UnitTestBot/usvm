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
        discoverProperties<TsTestValue, TsTestValue>(
            method,
            { x, r ->
                // Note: this is an attempt to represent `r == x["a"]`
                if (x !is TsTestValue.TsClass || r !is TsTestValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == r.number || (xa.number.isNaN() && r.number.isNaN())
            },
            { x, r ->
                (x is TsTestValue.TsUndefined || x is TsTestValue.TsNull) && r is TsTestValue.TsException
            }
        )
    }

    @Test
    fun `test dispatchOverField`() {
        val method = getMethod(className, "dispatchOverField")
        discoverProperties<TsTestValue, TsTestValue>(
            method,
            { x, r ->
                if (x !is TsTestValue.TsClass || r !is TsTestValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == 1.0 && r.number == 1.0
            },
            { x, r ->
                if (x !is TsTestValue.TsClass || r !is TsTestValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number == 2.0 && r.number == 2.0
            },
            { x, r ->
                if (x !is TsTestValue.TsClass || r !is TsTestValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsTestValue.TsNumber
                xa.number != 1.0 && xa.number != 2.0 && r.number == 100.0
            },
            { x, r ->
                (x is TsTestValue.TsUndefined || x is TsTestValue.TsNull) && r is TsTestValue.TsException
            }
        )
    }

    @Test
    fun `test returnSumOfTwoFields`() {
        val method = getMethod(className, "returnSumOfTwoFields")
        discoverProperties<TsTestValue, TsTestValue>(
            method,
            { x, r ->
                if (x !is TsTestValue.TsClass || r !is TsTestValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsTestValue.TsNumber
                val xb = x.properties["b"] as TsTestValue.TsNumber
                xa.number + xb.number == r.number
            },
            { x, r ->
                (x is TsTestValue.TsUndefined || x is TsTestValue.TsNull) && r is TsTestValue.TsException
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
