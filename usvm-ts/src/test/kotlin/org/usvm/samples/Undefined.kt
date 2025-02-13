package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsObject
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Undefined : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Undefined.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test isUndefined`() {
        val method = getMethod("Undefined", "isUndefined")
        discoverProperties<TsObject, TsObject.TsNumber>(
            method = method,
            { a, r -> a is TsObject.TsUndefinedObject && r.number == 1.0 },
            { a, r -> a !is TsObject.TsUndefinedObject && r.number == 2.0 },
        )
    }
}
