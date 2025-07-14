package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class PtrCall : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test call lambda`() {
        val method = getMethod(className, "callLambda")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }
}
