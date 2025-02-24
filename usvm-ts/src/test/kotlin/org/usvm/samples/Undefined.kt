package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class Undefined : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test isUndefined`() {
        val method = getMethod(className, "isUndefined")
        discoverProperties<TsValue, TsValue.TsNumber>(
            method = method,
            { a, r -> (a is TsValue.TsUndefined) && (r.number == 1.0) },
            { a, r -> (a !is TsValue.TsUndefined) && (r.number == 2.0) },
        )
    }
}
