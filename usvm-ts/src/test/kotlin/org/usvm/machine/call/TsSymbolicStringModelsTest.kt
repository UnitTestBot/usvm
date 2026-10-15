package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TsSymbolicStringModelsTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/SymbolicStringModels.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 4)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val tests = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            val analysis = machine.analyzeWithOutcome(methods = listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), "${method.name}: ${analysis.unsupportedPaths}")

            analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        }

        replay(method = method, tests = tests)
        tests
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
    fun `symbolic trim variants preserve the full ECMAScript whitespace set`() {
        val whitespaceCharacters = "\t\n\u000b\u000c\r \u00a0\u1680\u2028\u2029\u202f\u205f\u3000\ufeff" +
            ('\u2000'..'\u200a').joinToString(separator = "")
        val whitespace = whitespaceCharacters.toSet()
        val transforms = mapOf<String, (String) -> String>(
            "trim" to { value -> value.trim { it in whitespace } },
            "trimStart" to { value -> value.trimStart { it in whitespace } },
            "trimEnd" to { value -> value.trimEnd { it in whitespace } },
        )

        for ((name, transform) in transforms) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
                method = method(name),
                { input, result -> input.value.isEmpty() && result.value.isEmpty() },
                { input, result -> input.value.isNotEmpty() && result.value == transform(input.value) },
                invariants = arrayOf({ input, result -> result.value == transform(input.value) }),
            )
        }
    }

    @Test
    fun `symbolic slice and substring normalize numeric bounds and replay UTF16 witnesses`() {
        for (name in listOf("slice", "substring")) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsString>(
                method = method(name),
                { _, _, _, result -> result.value.isEmpty() },
                { _, _, _, result -> result.value.isNotEmpty() },
            )
        }
    }

    @Test
    fun `symbolic searches compare UTF16 units and preserve match positions`() {
        fun expected(value: String): Double {
            val first = value.indexOf('\ud800')
            val last = value.lastIndexOf('\ud800')
            val included = if ('\ud800' in value) 1 else 0
            val starts = if (value.startsWith('\ud800')) 1 else 0
            val ends = if (value.endsWith('\ud800')) 1 else 0

            return (first + 10 * last + 100 * included + 1000 * starts + 10_000 * ends).toDouble()
        }

        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("searches"),
            { input, result -> '\ud800' !in input.value && result.number == -11.0 },
            { input, result -> '\ud800' in input.value && result.number == expected(input.value) },
            invariants = arrayOf({ input, result -> result.number == expected(input.value) }),
        )
    }

    @Test
    fun `symbolic split and substitution preserve copied ranges and lone surrogates`() {
        val transforms = mapOf<String, (String) -> String>(
            "split" to { value -> value.split('\u0000').take(3).joinToString(separator = "|") },
            "replaceAll" to { value -> if (value.length > 2) "" else value.replace("\ud800", "\$\ud800") },
        )

        for ((name, transform) in transforms) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
                method = method(name),
                { input, result -> input.value.isEmpty() && result.value == transform(input.value) },
                { input, result -> input.value.isNotEmpty() && result.value == transform(input.value) },
                invariants = arrayOf({ input, result -> result.value == transform(input.value) }),
            )
        }
    }

    @Test
    fun `single character trim does not enumerate whitespace spellings`() {
        val method = method("trimSingle")

        val tests = runner(method, options)

        assertEquals(3, tests.size, "Only wrong length, whitespace, and non-whitespace paths are needed")
        assertEquals(setOf(-1.0, 0.0, 1.0), tests.map { assertIs<TsTestValue.TsNumber>(it.returnValue).number }.toSet())
    }

    @Test
    fun `copied ranges and concatenation keep their proven length bounds`() {
        val method = method("boundedCopy")

        val tests = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            val analysis = machine.analyzeWithOutcome(methods = listOf(method))
            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), analysis.unsupportedPaths.toString())

            analysis.states.map { state ->
                val test = TsTestResolver().resolve(method, state)
                val result = assertIs<TsTestValue.TsString>(test.returnValue).value
                if (result.isNotEmpty()) {
                    val completion = assertIs<TsMethodResult.Success>(state.methodResult)
                    val reference = completion.value.asExpr(state.ctx.addressSort)
                    assertEquals(3, state.boundedStringBackingRefs[reference])
                    val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
                    assertEquals(input.substring(1, 3) + "!", result)
                }

                test
            }
        }

        assertTrue(tests.any { assertIs<TsTestValue.TsString>(it.returnValue).value.isNotEmpty() })
        replay(method = method, tests = tests)
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "SymbolicStringModels")

    private fun replay(method: EtsMethod, tests: List<TsTest>) {
        assertTrue(tests.isNotEmpty(), "No witnesses for ${method.name}")

        val script = buildString {
            appendLine(source.readText())
            for ((index, test) in tests.withIndex()) {
                val arguments = test.before.parameters.joinToString { literal(it) }
                val expected = literal(test.returnValue)

                appendLine("if (new SymbolicStringModels().${method.name}($arguments) !== $expected) {")
                appendLine("  throw Error('${method.name} witness $index');")
                appendLine("}")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "symbolic-string-model-${method.name}",
            timeoutMessage = "String model witness replay timed out",
        )
    }

    private fun literal(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsNumber -> value.number.toString()
        else -> error("Unexpected string model test value: $value")
    }
}
