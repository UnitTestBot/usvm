package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Arrays : TsMethodTestRunner() {
    override val scene: EtsScene = run {
        val name = "Arrays.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testCreateConstantArrayOfNumbers() {
        val method = getMethod("Arrays", "createConstantArrayOfNumbers")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 }
            )
        )
    }

    @Test
    fun testCreateAndReturnConstantArrayOfNumbers() {
        val method = getMethod("Arrays", "createAndReturnConstantArrayOfNumbers")
        discoverProperties<TsValue.TsArray>(
            method = method,
            { r -> r.values.map { (it as TsValue.TsNumber.TsDouble).number } == listOf(1.0, 2.0, 3.0) },
        )
    }
}
