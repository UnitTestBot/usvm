package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertIs

private const val REPLAY_FAILURE_CONTEXT_LIMIT = 1000

class StringArrayIsolation : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/arrays/StringArrayIsolation.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `changing an input array does not change the string length`() {
        val method = getMethod(methodName = "independentArrayLength")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsArray<*>, TsTestValue.TsNumber>(
            method = method,
            { input, array, result ->
                input.value.length == 1 && array.values.size == 1 && result.number == 2.0
            },
            { input, array, result ->
                (input.value.length != 1 || array.values.size != 1) && result.number == 0.0
            },
            invariants = arrayOf(
                { _, _, result -> result.number != 1.0 },
            ),
        )
    }

    @Test
    fun `array isolation witnesses replay in JavaScript`() {
        val method = getMethod(methodName = "independentArrayLength")
        val tests = runner(method, options)
        val source = getResourcePath(tsPath).readText()

        val script = buildString {
            appendLine(source)
            tests.forEachIndexed { index, test ->
                val input = assertIs<TsTestValue.TsString>(test.before.parameters[0]).value
                val array = assertIs<TsTestValue.TsArray<*>>(test.before.parameters[1])
                val elements = array.values.joinToString { value ->
                    assertIs<TsTestValue.TsNumber>(value).number.toString()
                }
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                appendLine(
                    "if (new StringArrayIsolation().independentArrayLength(${jsString(input)}, [$elements]) !== $expected) {"
                )
                appendLine("  throw Error('array isolation witness $index');")
                appendLine("}")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-array-isolation",
            timeoutMessage = "Array isolation replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }
}
