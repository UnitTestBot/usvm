package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Undefined : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Undefined.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test isUndefined`() {
        val method = getMethod("Undefined", "isUndefined")
        discoverProperties<TSObject, TSObject.TSNumber>(
            method = method,
            { a, r -> a is TSObject.TSUndefinedObject && r.number == 1.0 },
            { a, r -> a !is TSObject.TSUndefinedObject && r.number == 2.0 },
        )
    }
}
