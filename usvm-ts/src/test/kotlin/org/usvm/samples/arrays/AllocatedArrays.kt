package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.test.util.checkers.noResultsExpected
import org.usvm.util.TsMethodTestRunner

class AllocatedArrays : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "arrays")

    @Test
    fun `test createConstantArrayOfNumbers`() {
        val method = getMethod(className, "createConstantArrayOfNumbers")
        discoverProperties<TsTestValue.TsNumber>(
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
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0) },
        )
    }

    @Test
    fun `test createAndAccessArrayOfBooleans`() {
        val method = getMethod(className, "createAndAccessArrayOfBooleans")
        discoverProperties<TsTestValue.TsNumber>(
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
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsBoolean>>(
            method = method,
            { r -> r.values.map { it.value } == listOf(true, false, true) },
        )
    }

    @Test
    fun `test createMixedArray`() {
        val method = getMethod(className, "createMixedArray")
        discoverProperties<TsTestValue.TsArray<*>>(
            method = method,
            { r ->
                if (r.values.size != 3) return@discoverProperties false
                val cond0 = r.values[0] is TsTestValue.TsNumber.TsDouble
                val cond1 = r.values[1] is TsTestValue.TsBoolean
                val cond2 = r.values[2] is TsTestValue.TsUndefined
                cond0 && cond1 && cond2
            },
        )
    }

    @Test
    fun `test createArrayOfUnknownValues`() {
        val method = getMethod(className, "createArrayOfUnknownValues")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue, TsTestValue.TsArray<*>>(
            method = method,
            { a, _, _, r -> r.values[0] == a && (a as TsTestValue.TsNumber).number == 1.1 },
            { _, b, _, r -> r.values[1] == b && (b as TsTestValue.TsBoolean).value },
            { _, _, c, r -> r.values[2] == c && c is TsTestValue.TsUndefined },
            invariants = arrayOf(
                { a, b, c, r -> r.values == listOf(a, b, c) }
            )
        )
    }

    @Test
    fun `test createArrayOfNumbersAndPutDifferentTypes`() {
        val method = getMethod(className, "createArrayOfNumbersAndPutDifferentTypes")
        checkMatches<TsTestValue.TsArray<*>>(
            method = method,
            noResultsExpected
        )
    }

    @Test
    fun `test allocatedArrayLengthExpansion`() {
        val method = getMethod(className, "allocatedArrayLengthExpansion")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException }
        )
    }

    @Test
    fun `test writeInTheIndexEqualToLength`() {
        val method = getMethod(className, "writeInTheIndexEqualToLength")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
        )
    }

    @Test
    fun `test readFakeObjectAndWriteFakeObject`() {
        val method = getMethod(className, "readFakeObjectAndWriteFakeObject")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue, TsTestValue>(
            method = method,
            { x, y, r ->
                val fst = x.values[0]
                val fstCondition = fst is TsTestValue.TsNumber && fst.number == 1.0
                val sndCondition = y is TsTestValue.TsNumber && y.number == 2.0
                val resultCondition = r is TsTestValue.TsArray<*> && r.values[0] == y

                fstCondition && sndCondition && resultCondition
            },
            { x, y, r ->
                val fst = x.values[0]
                val fstCondition = fst is TsTestValue.TsNumber && fst.number == 1.0
                val sndCondition = y !is TsTestValue.TsNumber || y.number != 2.0
                val resultCondition = r is TsTestValue.TsArray<*> && r.values[0] == y

                fstCondition && sndCondition && resultCondition
            },
            { x, y, r ->
                val fst = x.values[0]
                val condition = fst !is TsTestValue.TsNumber || fst.number != 1.0
                condition && r is TsTestValue.TsArray<*> && r.values == x.values
            },
        )
    }
}