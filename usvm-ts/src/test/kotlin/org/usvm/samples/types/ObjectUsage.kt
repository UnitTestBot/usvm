package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.Test

class ObjectUsage : TsMethodTestRunner() {
    private val tsPath = "/samples/types/ObjectUsage.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `object as parameter`() {
        val method = getMethod("objectAsParameter")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { _, r -> r eq 42 },
            invariants = arrayOf(
                { _, r -> r neq -1 }
            )
        )
    }
}
