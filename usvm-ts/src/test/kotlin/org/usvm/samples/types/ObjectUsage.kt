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
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { value, r -> value is TsTestValue.TsClass && value.name == "Object" && r.number == 42.0 },
            { value, r -> value == TsTestValue.TsUndefined && r.number == -1.0 }
        )
    }

}