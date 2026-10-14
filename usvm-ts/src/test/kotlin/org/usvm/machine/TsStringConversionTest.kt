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
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
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

class TsStringConversionTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/StringConversion.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(
        maxArraySize = 8,
        eraseTypeAssertions = true,
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
    )
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }
        val analysis = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer)
            .use { machine -> machine.analyzeWithOutcome(listOf(method)) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.isEmpty(), analysis.unsupportedPaths.toString())
        assertTrue(events.isEmpty(), events.toString())
        val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        assertTrue(tests.isNotEmpty(), method.name)
        replay(method.name, tests)
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
    fun `typed and generic string conversion preserves the original symbolic value`() {
        for (name in listOf("typed", "generic")) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
                method = getMethod(methodName = name, className = "StringConversion"),
                { input, result -> input.value == result.value },
                invariants = arrayOf({ input, result -> input.value == result.value }),
            )
        }
    }

    @Test
    fun `boolean conversion preserves both outcomes`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = getMethod(methodName = "boolean", className = "StringConversion"),
            { input, result -> input.value && result.value == "true" },
            { input, result -> !input.value && result.value == "false" },
            invariants = arrayOf({ input, result -> result.value == input.value.toString() }),
        )
    }

    @Test
    fun `omitted nullish concrete numeric and UTF16 conversion replays exactly`() {
        val cases = mapOf("constants" to "|undefined|null|true|false|0|1.5", "codeUnits" to "A\u0000\uD800\uDC00\uDFFF")

        for ((name, expected) in cases) {
            discoverProperties<TsTestValue.TsString>(
                method = getMethod(methodName = name, className = "StringConversion"),
                { result -> result.value == expected },
                invariants = arrayOf({ result -> result.value == expected }),
            )
        }
    }

    @Test
    fun `source bindings named String retain their callable semantics`() {
        for ((name, expected) in mapOf("shadowedParameter" to "shadow", "shadowedLocal" to "local")) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
                method = getMethod(methodName = name, className = "StringConversion"),
                { _, result -> result.value == expected },
                invariants = arrayOf({ _, result -> result.value == expected }),
            )
        }
    }

    @Test
    fun `object and symbolic number conversion remain explicit limitations`() {
        for (name in listOf("unsupportedObject", "symbolicNumber")) {
            val method = getMethod(methodName = name, className = "StringConversion")
            val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                    limitations += event
                }
            }

            val analysis = TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer)
                .use { machine -> machine.analyzeWithOutcome(listOf(method)) }

            assertTrue(analysis.states.isEmpty(), name)
            assertTrue(analysis.unsupportedPaths.isNotEmpty(), name)
            val conversionLimited = limitations.any {
                it.reason == TsRuntimeFeatureLimitationReason.STRING_PRIMITIVE_CONVERSION
            }
            assertTrue(conversionLimited, name)
        }
    }

    private fun replay(name: String, tests: List<TsTest>) {
        val script = buildString {
            appendLine(source.readText())
            for (test in tests) {
                val arguments = test.before.parameters.joinToString { value ->
                    when (value) {
                        is TsTestValue.TsString -> jsString(value.value)
                        is TsTestValue.TsBoolean -> value.value.toString()
                        else -> error("Unexpected conversion input: $value")
                    }
                }
                val expected = assertIs<TsTestValue.TsString>(test.returnValue).value
                appendLine(
                    "if (new StringConversion().$name($arguments) !== ${jsString(expected)}) throw new Error('$name');",
                )
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = name,
            timeoutMessage = "String replay timed out",
        )
    }
}
