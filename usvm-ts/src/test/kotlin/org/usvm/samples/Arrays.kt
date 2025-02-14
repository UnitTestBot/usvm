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
        discoverProperties<TsValue.TsArray<TsValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0) },
        )
    }

    @Test
    fun testCreateArrayOfBooleans() {
        val method = getMethod("Arrays", "createArrayOfBooleans")
        discoverProperties<TsValue.TsArray>(
            method = method,
            { r -> r.values.map { (it as TsValue.TsBoolean).value } == listOf(true, false, true) },
        )
    }

    @Test
    fun testCreateMixedArray() {
        val method = getMethod("Arrays", "createMixedArray")
        discoverProperties<TsValue>(
            method = method,
            { r ->
                val arrayCondition = r is TsValue.TsArray && r.values.size == 3
                if (!arrayCondition) return@discoverProperties false

                val firstElemCondition = r.values[0] is TsValue.TsNumber.TsDouble
                val secondElemCondition = r.values[1] is TsValue.TsBoolean
                val thirdElemCondition = r.values[2] is TsValue.TsUndefined

                firstElemCondition && secondElemCondition && thirdElemCondition
            },
        )
    }
}
