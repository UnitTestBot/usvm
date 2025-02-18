package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class MinValue : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "MinValue.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    @Disabled
    fun testMinValue() {
        val method = getMethod("MinValue", "findMinValue")
        discoverProperties<TsValue.TsArray<*>, TsValue.TsNumber>(
            method,
        )
    }
}
