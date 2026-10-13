package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TsStringIndexingTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/StringIndexing.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 4)

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            val analysis = machine.analyzeWithOutcome(listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), "${method.name}: ${analysis.unsupportedPaths}")
            analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `indexed reads preserve isolated surrogate units`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("indexedReadEquals"),
            { value -> value.value },
            invariants = arrayOf({ value -> value.value }),
        )
    }

    @Test
    fun `out of range string index yields undefined`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("outOfRangeIndex"),
            { value -> value.value },
            invariants = arrayOf({ value -> value.value }),
        )
    }

    @Test
    fun `indexing reads individual UTF-16 code units`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("literalUtf16"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `invalid numeric indexes return undefined without truncation or wrapping`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("invalidIndexes"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `string property keys use canonical indexes`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("stringKeys"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `symbolic numeric indexing explores characters and undefined`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicIndex"),
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            { _, result -> result.number == 3.0 },
            invariants = arrayOf({ index, result ->
                val number = index.number
                val isIndex = number.isFinite() && number >= 0.0 && number < 3.0 && number % 1.0 == 0.0
                val expected = if (isIndex) {
                    if ("aba"[number.toInt()] == 'a') 1.0 else 2.0
                } else {
                    3.0
                }

                result.number == expected
            }),
        )
    }

    @Test
    fun `symbolic string indexing preserves empty match and nonmatch branches`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("symbolicString"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            invariants = arrayOf({ input, result ->
                val expected = when {
                    input.value.isEmpty() -> 0.0
                    input.value.first() == 'a' -> 1.0
                    else -> 2.0
                }

                result.number == expected
            }),
        )
    }

    @Test
    fun `charAt supports symbolic strings and positions`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicCharAt"),
            { _, _, result -> result.number == 0.0 },
            { _, _, result -> result.number == 1.0 },
            { _, _, result -> result.number == 2.0 },
            invariants = arrayOf({ input, index, result ->
                val integer = if (index.number.isNaN()) 0.0 else index.number.toLong().toDouble()
                val character = if (integer >= 0.0 && integer < input.value.length) {
                    input.value[integer.toInt()].toString()
                } else {
                    ""
                }
                val expected = if (input.value.length > 2) 0.0 else if (character == "a") 1.0 else 2.0

                result.number == expected
            }),
        )
    }

    @Test
    fun `slice copies a symbolic code unit through ordinary indexing`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method("symbolicSlice"),
            { input, result -> input.value.length == 2 && result.value == input.value.take(1) },
            invariants = arrayOf({ input, result ->
                result.value == if (input.value.length == 2) input.value.take(1) else ""
            }),
        )
    }

    @Test
    fun `search reads symbolic receivers and patterns`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("symbolicSearch"),
            { _, result -> result.number == -2.0 },
            { _, result -> result.number == -1.0 },
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ input, result ->
                val expected = if (input.value.length > 2) -2.0 else input.value.indexOf('a').toDouble()

                result.number == expected
            }),
        )

        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("symbolicPattern"),
            { _, result -> result.number == -2.0 },
            { _, result -> result.number == -1.0 },
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ input, result ->
                val expected = if (input.value.length > 1) -2.0 else "aba".indexOf(input.value).toDouble()

                result.number == expected
            }),
        )
    }

    @Test
    fun `numeric code unit helpers compose on symbolic ASCII strings`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method("symbolicCase"),
            { input, result -> input.value.length == 1 && result.value.length == 2 },
            invariants = arrayOf({ input, result ->
                val value = input.value
                val isAsciiCharacter = value.length == 1 && value.first().code <= 127
                val expected = if (isAsciiCharacter) {
                    value.uppercase() + value.lowercase()
                } else {
                    ""
                }

                result.value == expected
            }),
        )
    }

    @Test
    fun `generated string indexing and model witnesses replay in JavaScript`() {
        val methodNames = listOf(
            "symbolicIndex",
            "symbolicString",
            "symbolicCharAt",
            "symbolicSlice",
            "symbolicSearch",
            "symbolicPattern",
            "symbolicCase",
        )
        val script = buildString {
            appendLine(source.readText())
            for (name in methodNames) {
                for (test in runner(method(name), options)) {
                    val arguments = test.before.parameters.joinToString { value -> jsValue(value) }
                    val expected = jsValue(test.returnValue)

                    appendLine("if (new StringIndexing().$name($arguments) !== $expected) throw Error('$name');")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-indexing",
            timeoutMessage = "String indexing replay timed out",
            failureContext = script.take(1000),
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "StringIndexing")

    private fun jsValue(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        else -> assertIs<TsTestValue.TsNumber>(value).number.toString()
    }
}
