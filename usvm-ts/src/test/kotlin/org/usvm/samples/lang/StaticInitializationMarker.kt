package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class StaticInitializationMarker : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticInitializationMarker.ts")

    @Test
    fun `source field does not replace the static initialization marker`() {
        val method = getMethod("readAndWrite", className = "StaticInitializationCollision")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 42 },
            invariants = arrayOf({ result -> result eq 42 }),
        )
    }

    @Test
    fun `independent static class still initializes`() {
        val method = getMethod("read", className = "StaticInitializationControl")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }
}
