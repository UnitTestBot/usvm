package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Null : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

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
