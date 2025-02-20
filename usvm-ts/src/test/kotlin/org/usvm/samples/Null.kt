package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Null : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene = run {
        val name = "$className.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test isNull`() {
        val method = getMethod(className, "isNull")
        discoverProperties<TsValue, TsValue.TsNumber>(
            method,
            { a, r -> (a is TsValue.TsNull) && (r.number == 1.0) },
            { a, r -> (a !is TsValue.TsNull) && (r.number == 2.0) },
        )
    }
}
