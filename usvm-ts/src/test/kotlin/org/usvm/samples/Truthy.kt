package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class Truthy : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = run {
        val name = "$className.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

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
