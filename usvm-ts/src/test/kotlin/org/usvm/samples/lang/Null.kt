package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class Null : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Null.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test isNull`() {
        val method = getMethod("isNull")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a is TsTestValue.TsNull) },
            { a, r -> (r eq 2) && (a !is TsTestValue.TsNull) },
        )
    }
}
