package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Undefined : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Undefined.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test isUndefined`() {
        val method = getMethod(className, "isUndefined")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, r -> (a is TsTestValue.TsUndefined) && (r.number == 1.0) },
            { a, r -> (a !is TsTestValue.TsUndefined) && (r.number == 2.0) },
        )
    }
}
