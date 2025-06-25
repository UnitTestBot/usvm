package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class ObjectUsage : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "types")

    @Test
    fun `object as parameter`() {
        val method = getMethod(className, "objectAsParameter")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { x, r -> r.number == 42.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 }
            )
        )
    }
}
