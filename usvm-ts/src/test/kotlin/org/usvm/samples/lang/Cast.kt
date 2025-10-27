package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Cast : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Cast.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `cast any to number`() {
        val method = getMethod("castAnyToNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
        )
    }
}
