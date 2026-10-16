package org.usvm.machine.expr

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
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
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

class RegExpRuntimeTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/RegExpRuntime.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(
        maxArraySize = 12,
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
        unknownCallFallback = TsResidualCallPolicy.STOP_PATH,
    )

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { target, machineOptions ->
        analyze(target = target, machineOptions = machineOptions, runtimeOptions = tsOptions)
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
    fun `symbolic DateUtils expression distinguishes failure and optional captures`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("dateMath"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            invariants = arrayOf({ _, result -> result.number in 0.0..2.0 }),
        )
    }

    @Test
    fun `symbolic fixed search and anchored runs find matches and failures`() {
        for (name in listOf("testLetters", "testRepeated")) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsBoolean>(
                method = method(name),
                { _, result -> result.value },
                { _, result -> !result.value },
            )
        }
    }

    @Test
    fun `anchored greedy run supports the default thousand code unit bound`() {
        val tests = analyze(
            target = method("testRepeated"),
            machineOptions = options,
            runtimeOptions = tsOptions.copy(maxArraySize = 1_000),
        )
        val values = tests.map { assertIs<TsTestValue.TsBoolean>(it.returnValue).value }.toSet()

        assertEquals(setOf(false, true), values)
    }

    @Test
    fun `captures metadata UTF16 end anchors and fresh arrays match native semantics`() {
        for (name in constantMethods) {
            discoverProperties<TsTestValue.TsBoolean>(
                method = method(name),
                { result -> result.value },
                invariants = arrayOf({ result -> result.value }),
            )
        }
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("overriddenTest"),
            { result -> !result.value },
            invariants = arrayOf({ result -> !result.value }),
        )
        discoverProperties<TsTestValue.TsNumber>(
            method = method("customMatch"),
            { result -> result.number == 7.0 },
            invariants = arrayOf({ result -> result.number == 7.0 }),
        )
    }

    @Test
    fun `core RegExp semantics are the same for all model and fallback profiles`() {
        for (selection in listOf(TsUnknownCallModelSelection.All, TsUnknownCallModelSelection.Only(emptySet()))) {
            for (fallback in TsResidualCallPolicy.entries) {
                val runtimeOptions = tsOptions.copy(
                    unknownCallModelSelection = selection,
                    unknownCallFallback = fallback,
                )
                val tests = analyze(method("captures"), options, runtimeOptions)

                assertTrue(tests.isNotEmpty())
                assertTrue(tests.all { assertIs<TsTestValue.TsBoolean>(it.returnValue).value })
            }
        }
    }

    @Test
    fun `unsupported syntax flags and coercion remain explicit limitations`() {
        val names = listOf(
            "unsupportedFlags",
            "unsupportedBacktracking",
            "unsupportedCoercion",
            "unsupportedUnanchoredRun",
            "unsupportedExecOverride",
            "unsupportedPropertyWrite",
            "unsupportedPropertyRead",
        )
        for (name in names) {
            TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
                val outcome = machine.analyzeWithOutcome(listOf(method(name)))

                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
                assertTrue(outcome.states.isEmpty(), name)
                assertTrue(outcome.unsupportedPaths.any { "REGULAR_EXPRESSION_LITERAL" in it }, "$name: $outcome")
            }
        }
    }

    @Test
    fun `every generated witness replays against Node RegExp`() {
        val names = constantMethods + listOf("dateMath", "testLetters", "testRepeated", "overriddenTest", "customMatch")
        val script = buildString {
            appendLine(source.readText())
            for (name in names) {
                val tests = runner(method(name), options)
                assertTrue(tests.isNotEmpty(), name)
                for (test in tests) {
                    val args = test.before.parameters.joinToString { jsValue(it) }
                    val expected = jsValue(test.returnValue)
                    appendLine("if (RegExpRuntime.$name($args) !== $expected) throw Error('$name');")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "regexp-runtime",
            timeoutMessage = "RegExp runtime replay timed out",
        )
    }

    private fun analyze(target: EtsMethod, machineOptions: UMachineOptions, runtimeOptions: TsOptions): List<TsTest> =
        TsMachine(scene = scene, options = machineOptions, tsOptions = runtimeOptions).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "${target.name}: ${outcome.unsupportedPaths}")
            outcome.states.map { state -> TsTestResolver().resolve(target, state) }
        }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "RegExpRuntime")

    private fun jsValue(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsBoolean -> value.value.toString()
        else -> assertIs<TsTestValue.TsNumber>(value).number.toString()
    }

    companion object {
        private val constantMethods = listOf(
            "captures",
            "missingCapture",
            "leftmost",
            "emptyCapture",
            "finalNewlines",
            "utf16AndEscapes",
            "freshResults",
            "lastIndex",
            "literalProperties",
            "bracketProperties",
        )
    }
}
