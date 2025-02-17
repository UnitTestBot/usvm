package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
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
    fun testCreateAndAccessArrayOfBooleans() {
        val method = getMethod("Arrays", "createAndAccessArrayOfBooleans")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 }
            )
        )
    }


    @Test
    fun testCreateArrayOfBooleans() {
        val method = getMethod("Arrays", "createArrayOfBooleans")
        discoverProperties<TsValue.TsArray<TsValue.TsBoolean>>(
            method = method,
            { r -> r.values.map { it.value } == listOf(true, false, true) },
        )
    }

    @Test
    fun testCreateMixedArray() {
        val method = getMethod("Arrays", "createMixedArray")
        discoverProperties<TsValue.TsArray<*>>(
            method = method,
            { r ->
                if (r.values.size != 3) return@discoverProperties false
                val cond0 = r.values[0] is TsValue.TsNumber.TsDouble
                val cond1 = r.values[1] is TsValue.TsBoolean
                val cond2 = r.values[2] is TsValue.TsUndefined
                cond0 && cond1 && cond2
            },
        )
    }

    @Test
    fun testCreateArrayOfUnknown() {
        val method = getMethod("Arrays", "createArrayOfUnknownValues")
        discoverProperties<TsValue, TsValue, TsValue, TsValue.TsArray<*>>(
            method = method,
            { a, _, _, r -> r.values[0] == a && (a as TsValue.TsNumber).number == 1.1 },
            { _, b, _, r -> r.values[1] == b && (b as TsValue.TsBoolean).value },
            { _, _, c, r -> r.values[2] == c && c is TsValue.TsUndefined },
            invariants = arrayOf(
                { a, b, c, r -> r.values == listOf(a, b, c) }
            )
        )
    }

    @Test
    @Disabled("Arrays should contain only fake objects")
    fun testCreateArrayOfNumbersAndPutDifferentTypes() {
        val method = getMethod("Arrays", "createArrayOfNumbersAndPutDifferentTypes")
        discoverProperties<TsValue.TsArray<*>>(
            method = method,
            { r ->
                val values = r.values
                values.size == 3
                        && (values[0] as TsValue.TsClass).properties.size == 1
                        && (values[1] as TsValue.TsBoolean).value
                        && values[2] is TsValue.TsUndefined
            },
        )
    }
}
