package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Equality : TSMethodTestRunner() {
    override val scene: EtsScene = run {
        val name = "Equality.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testTruthyTypes() {
        val method = getMethod("Equality", "truthyTypes")
        discoverProperties<TSObject.TSNumber>(
            method,
        )
    }
}