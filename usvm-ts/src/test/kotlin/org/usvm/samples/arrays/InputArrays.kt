package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class InputArrays : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "arrays")

    @Test
    fun testInputArrayOfNumbers() {
        val method = getMethod(className, "inputArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue>(
            method = method,
            { x, r -> r is TsTestValue.TsException },
            { x, r ->
                r as TsTestValue.TsNumber
                (x.values[0] as TsTestValue.TsNumber).number == 1.0 && r.number == 1.0
            },
            { x, r ->
                r as TsTestValue.TsNumber
                (x.values[0] as TsTestValue.TsNumber).number != 1.0 && r.number == 2.0
            },
            invariants = arrayOf(
                { _, r ->
                    r !is TsTestValue.TsNumber || r.number != -1.0
                }
            )
        )
    }

    @Test
    fun testWriteIntoInputArray() {
        val method = getMethod(className, "writeIntoInputArray")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.values[0].number == 1.0 && r.number == 1.0 },
            { x, r -> x.values[0].number != 1.0 && r.number == 1.0 },
        )
    }

    @Test
    fun testIdForArrayOfNumbers() {
        val method = getMethod(className, "idForArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { x, r -> x.values == r.values },
        )
    }

    @Test
    fun testArrayOfBooleans() {
        val method = getMethod(className, "arrayOfBooleans")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsBoolean>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.values[0].value == true && r.number == 1.0 },
            { x, r -> x.values[0].value != true && r.number == -1.0 },
        )
    }

    @Test
    fun testArrayOfUnknownValues() {
        val method = getMethod(className, "arrayOfUnknownValues")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue.TsArray<TsTestValue>>(
            method = method,
            // TODO exception
            { x, r ->
                val firstElement = x.values[0]
                firstElement is TsTestValue.TsNumber && firstElement.number == 1.1 && x.values == r.values
            },
            { x, r ->
                val firstElement = x.values[0]
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || firstElement.number != 1.1

                val secondElement = x.values[1]
                val secondElementCondition = secondElement is TsTestValue.TsBoolean && secondElement.value

                firstElementCondition && secondElementCondition && x.values == r.values
            },
            { x, r ->
                val firstElement = x.values[0]
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || firstElement.number != 1.1

                val secondElement = x.values[1]
                val secondElementCondition = secondElement !is TsTestValue.TsBoolean || !secondElement.value

                val thirdElement = x.values[2]
                val thirdElementCondition = thirdElement is TsTestValue.TsUndefined

                firstElementCondition && secondElementCondition && thirdElementCondition && x.values == r.values
            }
        )
    }

    @Test
    fun testWriteIntoArrayOfUnknownValues() {
        val method = getMethod(className, "writeIntoArrayOfUnknownValues")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue.TsArray<TsTestValue>>(
            method = method,
            { _, r ->
                val fst = r.values[0] is TsTestValue.TsNull
                val sndElement = r.values[1]
                val snd = sndElement is TsTestValue.TsBoolean && sndElement.value
                val trd = r.values[2] is TsTestValue.TsUndefined

                fst && snd && trd
            },
            // TODO add exceptions
        )
    }

    @Test
    fun testRewriteFakeValueInArray() {
        val method = getMethod(className, "rewriteFakeValueInArray")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue>(
            method = method,
            { x, r ->
                val value = x.values[0]
                value is TsTestValue.TsNumber && value.number == 1.0 && r is TsTestValue.TsNull
            },
            { x, r ->
                val value = x.values[0]
                value !is TsTestValue.TsNumber || value.number != 1.0 && r == value
            },
        )
    }
}
