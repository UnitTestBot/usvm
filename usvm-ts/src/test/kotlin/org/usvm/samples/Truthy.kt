package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Truthy : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test arrayTruthy`() {
        val method = getMethod(className, "arrayTruthy")
        discoverProperties<TsValue.TsNumber>(
            method,
            { r -> r.number == 0.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 },
            )
        )
    }
}
