package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class MinValue : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/MinValue.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    @Disabled
    fun `test findMinValue`() {
        val method = getMethod("findMinValue")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsNumber>(
            method,
        )
    }
}
