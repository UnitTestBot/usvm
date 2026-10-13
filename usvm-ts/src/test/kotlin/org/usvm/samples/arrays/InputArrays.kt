package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import org.usvm.util.neq
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InputArrays : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/arrays/InputArrays.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testInputArrayOfNumbers() {
        val method = getMethod("inputArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue>(
            method = method,
            { x, r ->
                r as TsTestValue.TsNumber
                val firstElement = x.values.firstOrNull()
                (r eq 1) && firstElement is TsTestValue.TsNumber && (firstElement eq 1)
            },
            { x, r ->
                r as TsTestValue.TsNumber
                val firstElement = x.values.firstOrNull()
                val firstElementIsUndefined = firstElement == null || firstElement is TsTestValue.TsUndefined
                (r eq -1) && firstElementIsUndefined
            },
            { x, r ->
                r as TsTestValue.TsNumber
                val firstElement = x.values.firstOrNull()
                (r eq 2) && firstElement is TsTestValue.TsNumber && (firstElement neq 1)
            },
        )
    }

    @Test
    fun testWriteIntoInputArray() {
        val method = getMethod(methodName = "writeIntoInputArray")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                val first = x.values.firstOrNull()
                (r eq 1) && first != null && (first eq 1)
            },
            { x, r ->
                val first = x.values.firstOrNull()
                (r eq 1) && (first == null || (first neq 1))
            },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    @Test
    fun `writing a nonempty input retains both original first element branches`() {
        val method = getMethod(methodName = "writeIntoNonEmptyInputArray")

        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq 0) && x.values.isEmpty() },
            { x, r ->
                val first = x.values.firstOrNull()
                (r eq 1) && first != null && (first eq 1)
            },
            { x, r ->
                val first = x.values.firstOrNull()
                (r eq 2) && first != null && (first neq 1)
            },
            invariants = arrayOf({ input, result ->
                val first = input.values.firstOrNull()
                val expected = when {
                    first == null -> 0
                    first eq 1 -> 1
                    else -> 2
                }
                result eq expected
            }),
        )
    }

    @Test
    fun `input array append and overwrite witnesses replay in native JavaScript`() {
        val source = getResourcePath(tsPath).readText()
        val methodNames = listOf("writeIntoInputArray", "writeIntoNonEmptyInputArray")
        val script = buildString {
            appendLine(source)
            appendLine("function assertOriginalWrite(input) {")
            appendLine("  const expected = [1, ...input.slice(1)];")
            appendLine("  const result = new InputArrays().writeIntoInputArray(input);")
            appendLine("  const valuesMatch = input.every((value, index) => Object.is(value, expected[index]));")
            appendLine("  if (result !== 1 || input.length !== expected.length || !valuesMatch) {")
            appendLine("    throw new Error('Original input write failed to preserve length or tail');")
            appendLine("  }")
            appendLine("}")
            appendLine("assertOriginalWrite([2, 7, 11]);")
            appendLine("assertOriginalWrite([NaN, -0, Infinity]);")

            for (methodName in methodNames) {
                val method = getMethod(methodName = methodName)
                val tests = runner(method, options)
                assertTrue(tests.isNotEmpty(), methodName)

                for (test in tests) {
                    val before = assertIs<TsTestValue.TsArray<*>>(test.before.parameters.single())
                    val after = assertIs<TsTestValue.TsArray<*>>(test.after.parameters.single())
                    val expectedResult = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                    val beforeValues = before.values.joinToString { value ->
                        assertIs<TsTestValue.TsNumber>(value).number.toString()
                    }
                    val afterValues = after.values.joinToString { value ->
                        assertIs<TsTestValue.TsNumber>(value).number.toString()
                    }

                    appendLine("{")
                    appendLine("  assertOriginalWrite([$beforeValues]);")
                    appendLine("  const actual = [$beforeValues];")
                    appendLine("  const expected = [$afterValues];")
                    appendLine("  const result = new InputArrays().$methodName(actual);")
                    appendLine("  const valuesMatch = actual.every((value, index) =>")
                    appendLine("    Object.is(value, expected[index]));")
                    appendLine("  const resultMatches = Object.is(result, $expectedResult);")
                    appendLine("  if (!resultMatches || actual.length !== expected.length || !valuesMatch) {")
                    appendLine("    throw new Error('$methodName changed its result or post-call array');")
                    appendLine("  }")
                    appendLine("}")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "input-array-append-and-overwrite",
            timeoutMessage = "Input array append and overwrite replay timed out",
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
            { x, r -> (r eq -1) && x.values.firstOrNull()?.value != true },
        )
    }

    @Test
    fun testArrayOfUnknownValues() {
        val method = getMethod("arrayOfUnknownValues")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue.TsArray<TsTestValue>>(
            method = method,
            // TODO exception
            { x, r ->
                val firstElement = x.values.firstOrNull()
                (r.values == x.values) && firstElement is TsTestValue.TsNumber && (firstElement eq 1.1)
            },
            { x, r ->
                val firstElement = x.values.firstOrNull()
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || (firstElement neq 1.1)

                val secondElement = x.values.getOrNull(1)
                val secondElementCondition = secondElement is TsTestValue.TsBoolean && secondElement.value

                (r.values == x.values) && firstElementCondition && secondElementCondition
            },
            { x, r ->
                val firstElement = x.values.firstOrNull()
                val firstElementCondition = firstElement !is TsTestValue.TsNumber || (firstElement neq 1.1)

                val secondElement = x.values.getOrNull(1)
                val secondElementCondition = secondElement !is TsTestValue.TsBoolean || !secondElement.value

                val thirdElement = x.values.getOrNull(2)
                val thirdElementCondition = thirdElement == null || thirdElement is TsTestValue.TsUndefined
                val resultMatches = r.values.size == x.values.size && r.values.zip(x.values).all { (actual, expected) ->
                    actual == expected ||
                        actual is TsTestValue.TsClass && expected is TsTestValue.TsClass && actual.name == expected.name
                }

                resultMatches && firstElementCondition && secondElementCondition && thirdElementCondition
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
                val value = x.values.firstOrNull()
                val valueIsNotOne = value !is TsTestValue.TsNumber || (value neq 1)
                val resultMatches = if (value == null) r is TsTestValue.TsUndefined else r == value

                valueIsNotOne && resultMatches
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
                val fst = x.values.firstOrNull()
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

    @RepeatedTest(10)
    fun `test getLength`() {
        val method = getMethod("getLength")
        discoverProperties<TsTestValue.TsArray<TsTestValue>, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq x.values.size) },
        )
    }
}
