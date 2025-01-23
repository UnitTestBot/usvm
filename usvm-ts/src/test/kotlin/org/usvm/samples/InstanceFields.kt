package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class InstanceFields : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "InstanceFields.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testReturnSingleField() {
        val method = getMethod("InstanceFields", "returnSingleField")
        discoverProperties<TSObject.TSClass, TSObject.TSNumber>(
            method,
            { x, r ->
                // Note: this is an attempt to represent `r == x["a"]`
                val xa = x.properties["a"] as TSObject.TSNumber
                xa.number == r.number || (xa.number.isNaN() && r.number.isNaN())
            },
        )
    }

    @Test
    fun testDispatchOverField() {
        val method = getMethod("InstanceFields", "dispatchOverField")
        discoverProperties<TSObject.TSClass, TSObject.TSNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TSObject.TSNumber
                xa.number == 1.0 && r.number == 1.0
            },
            { x, r ->
                val xa = x.properties["a"] as TSObject.TSNumber
                xa.number == 2.0 && r.number == 2.0
            },
            { x, r ->
                val xa = x.properties["a"] as TSObject.TSNumber
                xa.number != 1.0 && xa.number != 2.0 && r.number == 100.0
            },
        )
    }

    @Test
    fun testReturnSumOfTwoFields() {
        val method = getMethod("InstanceFields", "returnSumOfTwoFields")
        discoverProperties<TSObject.TSClass, TSObject.TSNumber>(
            method,
            { x, r ->
                val xa = x.properties["a"] as TSObject.TSNumber
                val xb = x.properties["b"] as TSObject.TSNumber
                xa.number + xb.number == r.number
            },
        )
    }
}
