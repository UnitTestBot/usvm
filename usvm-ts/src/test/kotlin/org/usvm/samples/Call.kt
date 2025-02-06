package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Call : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Call.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

    @Test
    fun `test simpleCall`() {
        val method = getMethod("Call", "simpleCall")
        discoverProperties<TSObject.TSNumber>(
            method = method,
            { r -> r.number == 42.0 },
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }
}
