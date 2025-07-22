package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Null : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Null.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test isNull`() {
        val method = getMethod(className, "isNull")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r -> (a is TsTestValue.TsNull) && (r.number == 1.0) },
            { a, r -> (a !is TsTestValue.TsNull) && (r.number == 2.0) },
        )
    }
}
