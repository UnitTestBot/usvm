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
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
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

class StringIteratorSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/StringIteratorSource.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 8)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }
        }

        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer).use { machine ->
            val outcome = machine.analyzeWithOutcome(methods = listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, method.name)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "${method.name}: ${outcome.unsupportedPaths}")
            assertTrue(limitations.isEmpty(), "${method.name}: $limitations")
            outcome.states.map { state -> TsTestResolver().resolve(method, state) }
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
    fun `string iteration combines surrogate pairs and preserves isolated units`() {
        for (methodName in literalMethods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(methodName),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `symbolic iteration explores empty lone unit pair and separate unit branches`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("symbolicWidth"),
            { _, result -> result.number == -1.0 },
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            { _, result -> result.number == 3.0 },
            invariants = arrayOf({ input, result ->
                val value = input.value
                val expected = when {
                    value.length > 2 -> -1
                    value.isEmpty() -> 0
                    value.length == 1 -> 1
                    Character.isSurrogatePair(value[0], value[1]) -> 2
                    else -> 3
                }
                result.number == expected.toDouble()
            }),
        )
    }

    @Test
    fun `symbolic iterator result retains exact UTF16 payload`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method("symbolicFirst"),
            { _, result -> result.value.isEmpty() },
            { _, result -> result.value.length == 1 },
            { _, result -> result.value.length == 2 },
            invariants = arrayOf({ input, result ->
                val value = input.value
                val expected = when {
                    value.length != 2 -> ""
                    Character.isSurrogatePair(value[0], value[1]) -> value
                    else -> value.take(1)
                }
                result.value == expected
            }),
        )
    }

    @Test
    fun `literal and generated symbolic witnesses replay with native string iteration`() {
        val script = buildString {
            appendLine(source.readText())
            for (methodName in literalMethods) {
                appendLine("if (StringIteratorSource.$methodName() !== 1) throw Error('$methodName');")
            }
            for (methodName in listOf("symbolicWidth", "symbolicFirst")) {
                val tests = runner(method(methodName), options)
                assertTrue(tests.isNotEmpty(), methodName)
                for (test in tests) {
                    val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
                    val expected = when (val result = test.returnValue) {
                        is TsTestValue.TsString -> jsString(result.value)
                        else -> assertIs<TsTestValue.TsNumber>(result).number.toString()
                    }
                    val invocation = "StringIteratorSource.$methodName(${jsString(input)})"
                    appendLine("if ($invocation !== $expected) throw Error('$methodName');")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-iterator",
            timeoutMessage = "String iterator replay timed out",
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "StringIteratorSource")

    private val literalMethods = listOf(
        "mixedUtf16",
        "reversedSurrogates",
        "destructuring",
        "exhaustedDestructuring",
        "reboundSource",
        "shadowedSymbol",
    )
}
