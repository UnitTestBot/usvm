package org.usvm.samples.lang

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

class SymbolicStringInput : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/SymbolicStringInput.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `symbolic string input is preserved in the result`() {
        val method = getMethod(methodName = "identity")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { input, result -> result.value == input.value },
        )
    }

    @Test
    fun `empty and one-character strings produce distinct results`() {
        val method = getMethod(methodName = "lengthOne")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { input, result -> input.value.isEmpty() && result.number == 0.0 },
            { input, result -> input.value.length == 1 && result.number == 1.0 },
            invariants = arrayOf(
                { input, result -> result.number == if (input.value.length == 1) 1.0 else 0.0 },
            ),
        )
    }

    @Test
    fun `string inferred from any preserves all length outcomes`() {
        val method = getMethod(methodName = "anyStringLength")

        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { input, result -> input !is TsTestValue.TsString && result.number == 0.0 },
            { input, result -> input is TsTestValue.TsString && input.value.length == 1 && result.number == 1.0 },
            { input, result -> input is TsTestValue.TsString && input.value.length != 1 && result.number == 2.0 },
            invariants = arrayOf(
                { input, result ->
                    val expected = when {
                        input !is TsTestValue.TsString -> 0.0
                        input.value.length == 1 -> 1.0
                        else -> 2.0
                    }

                    result.number == expected
                },
            ),
        )
    }

    @Test
    fun `literal preserves NUL non-ASCII and a surrogate pair`() {
        val method = getMethod(methodName = "literal")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { result -> result.value == "A\u0000\u03a9\uD83D\uDE00" },
        )
    }

    @Test
    fun `literal length counts UTF-16 code units`() {
        val method = getMethod(methodName = "literalLength")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result.number == 5.0 },
        )
    }

    @Test
    fun `generated string witnesses replay in JavaScript`() {
        val methodNames = listOf("identity", "lengthOne", "literal", "literalLength")
        val tests = methodNames.associateWith { name -> runner(getMethod(methodName = name), options) }
        val source = getResourcePath(tsPath).readText()

        val script = buildString {
            appendLine(source)
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val arguments = test.before.parameters.joinToString { value ->
                        jsString(assertIs<TsTestValue.TsString>(value).value)
                    }
                    val expected = when (val result = test.returnValue) {
                        is TsTestValue.TsString -> jsString(result.value)
                        is TsTestValue.TsNumber -> result.number.toString()
                        else -> error("Unexpected result for $name: $result")
                    }

                    appendLine("if (new SymbolicStringInput().$name($arguments) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "symbolic-strings",
            timeoutMessage = "Symbolic string replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }
}
