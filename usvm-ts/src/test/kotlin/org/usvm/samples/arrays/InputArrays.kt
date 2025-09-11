package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq

class InputArrays : TsMethodTestRunner() {
    private val tsPath = "/samples/arrays/InputArrays.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testInputArrayOfNumbers() {
        val method = getMethod("inputArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue>(
            method = method,
            { _, r -> r is TsTestValue.TsException },
            { x, r ->
                r as TsTestValue.TsNumber
                val x0 = x.values[0] as TsTestValue.TsNumber
                (r eq 1) && (x0 eq 1)
            },
            { x, r ->
                r as TsTestValue.TsNumber
                val x0 = x.values[0] as TsTestValue.TsNumber
                (r eq 2) && (x0 neq 1)
            },
            invariants = arrayOf(
                { _, r ->
                    r !is TsTestValue.TsNumber || (r neq -1)
                }
            )
        )
    }

    @Test
    fun testWriteIntoInputArray() {
        val method = getMethod("writeIntoInputArray")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq 1) && (x.values[0] eq 1) },
            { x, r -> (r eq 1) && (x.values[0] neq 1) },
        )
    }

    @Test
    fun testIdForArrayOfNumbers() {
        val method = getMethod("idForArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { x, r -> x.values == r.values },
        )
    }

    @Test
    fun testArrayOfBooleans() {
        val method = getMethod("arrayOfBooleans")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsBoolean>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq 1) && x.values[0].value },
            { x, r -> (r eq -1) && !x.values[0].value },
        )
    }

    @Test
    fun testArrayOfUnknownValues() {
        val method = getMethod("arrayOfUnknownValues")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue.TsArray<TsTestValue>>(
            method = method,
            // TODO exception
            { x, r ->
                val firstElement = x.values[0]
                (r.values == x.values) && firstElement is TsTestValue.TsNumber && (firstElement eq 1.1)
            },
            { x, r ->
                val firstElement = x.values[0]
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || (firstElement neq 1.1)

                val secondElement = x.values[1]
                val secondElementCondition = secondElement is TsTestValue.TsBoolean && secondElement.value

                (r.values == x.values) && firstElementCondition && secondElementCondition
            },
            { x, r ->
                val firstElement = x.values[0]
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || (firstElement neq 1.1)

                val secondElement = x.values[1]
                val secondElementCondition = secondElement !is TsTestValue.TsBoolean || !secondElement.value

                val thirdElement = x.values[2]
                val thirdElementCondition = thirdElement is TsTestValue.TsUndefined

                (r.values == x.values) && firstElementCondition && secondElementCondition && thirdElementCondition
            }
        )
    }

    @Test
    fun testWriteIntoArrayOfUnknownValues() {
        val method = getMethod("writeIntoArrayOfUnknownValues")
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
        val method = getMethod("rewriteFakeValueInArray")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue>(
            method = method,
            { x, r ->
                val value = x.values[0]
                r is TsTestValue.TsNull && value is TsTestValue.TsNumber && (value eq 1)
            },
            { x, r ->
                val value = x.values[0]
                (r == value) && (value !is TsTestValue.TsNumber || (value neq 1))
            },
        )
    }

    @Test
    fun `test readFakeObjectAndWriteFakeObject`() {
        val method = getMethod("readFakeObjectAndWriteFakeObject")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue, TsTestValue>(
            method = method,
            { x, y, r ->
                val fst = x.values[0]
                val fstCondition = fst is TsTestValue.TsNumber && (fst eq 1)
                val sndCondition = y is TsTestValue.TsNumber && (y eq 2)
                val resultCondition = r is TsTestValue.TsArray<*> && r.values[0] == y

                resultCondition && fstCondition && sndCondition
            },
            { x, y, r ->
                val fst = x.values[0]
                val fstCondition = fst is TsTestValue.TsNumber && (fst eq 1)
                val sndCondition = y !is TsTestValue.TsNumber || (y neq 2)
                val resultCondition = r is TsTestValue.TsArray<*> && r.values[0] == y

                resultCondition && fstCondition && sndCondition
            },
            { x, y, r ->
                val fst = x.values[0]
                val condition = fst !is TsTestValue.TsNumber || (fst neq 1)
                condition && r is TsTestValue.TsArray<*> && r.values == x.values
            },
        )
    }

    @Test
    fun `test conditionalLength`() {
        val method = getMethod("conditionalLength")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                (r eq 1) && (x.number > 0.0)
            },
        )
    }
}
