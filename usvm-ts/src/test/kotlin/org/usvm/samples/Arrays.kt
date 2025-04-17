package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

@Disabled("Arrays are not fully supported, tests are unstable on CI")
class Arrays : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test createConstantArrayOfNumbers`() {
        val method = getMethod(className, "createConstantArrayOfNumbers")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 }
            )
        )
    }

    @Test
    fun `test createAndReturnConstantArrayOfNumbers`() {
        val method = getMethod(className, "createAndReturnConstantArrayOfNumbers")
        discoverProperties<TsValue.TsArray<TsValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0) },
        )
    }

    @Test
    fun `test createAndAccessArrayOfBooleans`() {
        val method = getMethod(className, "createAndAccessArrayOfBooleans")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 }
            )
        )
    }

    @Test
    fun `test createArrayOfBooleans`() {
        val method = getMethod(className, "createArrayOfBooleans")
        discoverProperties<TsValue.TsArray<TsValue.TsBoolean>>(
            method = method,
            { r -> r.values.map { it.value } == listOf(true, false, true) },
        )
    }

    @Test
    fun `test createMixedArray`() {
        val method = getMethod(className, "createMixedArray")
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
    fun `test createArrayOfUnknownValues`() {
        val method = getMethod(className, "createArrayOfUnknownValues")
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
    fun `test createArrayOfNumbersAndPutDifferentTypes`() {
        val method = getMethod(className, "createArrayOfNumbersAndPutDifferentTypes")
        discoverProperties<TsValue.TsArray<*>>(
            method = method,
            { r ->
                val values = r.values
                values.size == 3
                    && values[0] is TsValue.TsNull
                    && (values[1] as TsValue.TsBoolean).value
                    && values[2] is TsValue.TsUndefined
            },
        )
    }

    @Test
    fun `test allocatedArrayLengthExpansion`() {
        val method = getMethod(className, "allocatedArrayLengthExpansion")
        discoverProperties<TsValue.TsArray<*>>(
            method = method,
            { r ->
                r.values.size == 6
                    && (r.values[0] as TsValue.TsNumber).number == 1.0
                    && (r.values[1] as TsValue.TsNumber).number == 2.0
                    && (r.values[2] as TsValue.TsNumber).number == 3.0
                    && r.values[3] is TsValue.TsUndefined
                    && r.values[4] is TsValue.TsUndefined
                    && (r.values[5] as TsValue.TsNumber).number == 5.0
            }
        )
    }

    @Test
    fun `test writeInTheIndexEqualToLength`() {
        val method = getMethod(className, "writeInTheIndexEqualToLength")
        discoverProperties<TsValue.TsArray<TsValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0, 4.0) },
        )
    }
}
