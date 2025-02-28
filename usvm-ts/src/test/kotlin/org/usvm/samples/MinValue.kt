package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class MinValue : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    @Disabled
    fun `test findMinValue`() {
        val method = getMethod(className, "findMinValue")
        discoverProperties<TsValue.TsArray<*>, TsValue.TsNumber>(
            method,
        )
    }
}
