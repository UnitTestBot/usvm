package org.usvm.samples

import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.fixEnums
import kotlin.test.Test

class Enums : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test enumOrdinal`() {
        val method = getMethod(className, "enumOrdinal")
        (method as EtsMethodImpl)._cfg = scene.fixEnums(method.cfg)
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }
}
