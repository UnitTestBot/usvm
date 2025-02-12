package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TsObject
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class InstanceFields : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "InstanceFields.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testReturnSingleField() {
        val method = getMethod("InstanceFields", "returnSingleField")
        discoverProperties<TsObject, TsObject>(
            method,
            { x, r ->
                // Note: this is an attempt to represent `r == x["a"]`
                if (x !is TsObject.TsClass || r !is TsObject.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsObject.TsNumber
                xa.number == r.number || (xa.number.isNaN() && r.number.isNaN())
            },
            { x, r ->
                (x is TsObject.TsUndefinedObject || x is TsObject.TsNull) && r is TsObject.TsException
            }
        )
    }

    @Test
    fun testDispatchOverField() {
        val method = getMethod("InstanceFields", "dispatchOverField")
        discoverProperties<TsObject, TsObject>(
            method,
            { x, r ->
                if (x !is TsObject.TsClass || r !is TsObject.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsObject.TsNumber
                xa.number == 1.0 && r.number == 1.0
            },
            { x, r ->
                if (x !is TsObject.TsClass || r !is TsObject.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsObject.TsNumber
                xa.number == 2.0 && r.number == 2.0
            },
            { x, r ->
                if (x !is TsObject.TsClass || r !is TsObject.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsObject.TsNumber
                xa.number != 1.0 && xa.number != 2.0 && r.number == 100.0
            },
            { x, r ->
                (x is TsObject.TsUndefinedObject || x is TsObject.TsNull) && r is TsObject.TsException
            }
        )
    }

    @Test
    fun testReturnSumOfTwoFields() {
        val method = getMethod("InstanceFields", "returnSumOfTwoFields")
        discoverProperties<TsObject, TsObject>(
            method,
            { x, r ->
                if (x !is TsObject.TsClass || r !is TsObject.TsNumber) return@discoverProperties false

                val xa = x.properties["a"] as TsObject.TsNumber
                val xb = x.properties["b"] as TsObject.TsNumber
                xa.number + xb.number == r.number
            },
            { x, r ->
                (x is TsObject.TsUndefinedObject || x is TsObject.TsNull) && r is TsObject.TsException
            }
        )
    }
}
