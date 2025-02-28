package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class InstanceFields : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test returnSingleField`() {
        val method = getMethod(className, "returnSingleField")
        discoverProperties<TsValue, TsValue>(
            method,
            { x, r ->
                // Note: this is an attempt to represent `r == x["a"]`
                if (x !is TsValue.TsClass || r !is TsValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsValue.TsNumber
                xa.number == r.number || (xa.number.isNaN() && r.number.isNaN())
            },
            { x, r ->
                (x is TsValue.TsUndefined || x is TsValue.TsNull) && r is TsValue.TsException
            }
        )
    }

    @Test
    fun `test dispatchOverField`() {
        val method = getMethod(className, "dispatchOverField")
        discoverProperties<TsValue, TsValue>(
            method,
            { x, r ->
                if (x !is TsValue.TsClass || r !is TsValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsValue.TsNumber
                xa.number == 1.0 && r.number == 1.0
            },
            { x, r ->
                if (x !is TsValue.TsClass || r !is TsValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsValue.TsNumber
                xa.number == 2.0 && r.number == 2.0
            },
            { x, r ->
                if (x !is TsValue.TsClass || r !is TsValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsValue.TsNumber
                xa.number != 1.0 && xa.number != 2.0 && r.number == 100.0
            },
            { x, r ->
                (x is TsValue.TsUndefined || x is TsValue.TsNull) && r is TsValue.TsException
            }
        )
    }

    @Test
    fun `test returnSumOfTwoFields`() {
        val method = getMethod(className, "returnSumOfTwoFields")
        discoverProperties<TsValue, TsValue>(
            method,
            { x, r ->
                if (x !is TsValue.TsClass || r !is TsValue.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsValue.TsNumber
                val xb = x.properties["b"] as TsValue.TsNumber
                xa.number + xb.number == r.number
            },
            { x, r ->
                (x is TsValue.TsUndefined || x is TsValue.TsNull) && r is TsValue.TsException
            }
        )
    }

    @Test
    fun `test assignField`() {
        val method = getMethod(className, "assignField")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method,
            { x, r -> r.number == 10.0 },
        )
    }
}
