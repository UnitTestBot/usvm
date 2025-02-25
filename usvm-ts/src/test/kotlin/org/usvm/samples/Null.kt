package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.RepeatedTest
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class Null : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @RepeatedTest(20)
    fun testIsNull() {
        val method = getMethod(className, "isNull")
        discoverProperties<TsValue, TsValue.TsNumber>(
            method,
            { a, r -> (a is TsValue.TsNull) && (r.number == 1.0) },
            { a, r -> (a !is TsValue.TsNull) && (r.number == 2.0) },
        )
    }
}
