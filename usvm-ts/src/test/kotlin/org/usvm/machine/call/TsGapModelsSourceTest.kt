package org.usvm.machine.call

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
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
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

class TsGapModelsSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/GapModelSource.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 16, eraseTypeAssertions = true)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val calls = mutableListOf<TsUnknownCallEvent>()
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                calls += event
            }

            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }
        }
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer).use { machine ->
            val analysis = machine.analyzeWithOutcome(listOf(method))
            val residualCalls = calls.filter { event -> event.outcome == TsUnknownCallOutcome.PATH_STOPPED }
            val diagnostics = "${method.name}: stop=${analysis.stopReason}, states=${analysis.states.size}, " +
                "unsupported=${analysis.unsupportedPaths}, residual=$residualCalls, limitations=$limitations"

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason, diagnostics)
            assertTrue(analysis.states.isNotEmpty(), diagnostics)
            assertTrue(analysis.unsupportedPaths.isEmpty(), diagnostics)
            assertTrue(residualCalls.isEmpty(), diagnostics)
            assertTrue(limitations.isEmpty(), diagnostics)
            analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `split and reduce preserve source values aliases and ignored argument effects`() {
        for (name in listOf("splitEdges", "reduceAliases", "reduceEmptyError", "ignoredArguments")) {
            discoverProperties<TsTestValue.TsBoolean>(
                method = method(name),
                { result -> result.value },
                invariants = arrayOf({ result -> result.value }),
            )
        }
    }

    @Test
    fun `join preserves composition nullish elements and runtime boolean overrides`() {
        val expectedValues = mapOf(
            "composedMutations" to "cba",
            "independentArrays" to "abcd",
            "joinRuntimeKinds" to "--true-false",
            "joinNumbers" to "1,2,3",
        )

        for ((name, expected) in expectedValues) {
            discoverProperties<TsTestValue.TsString>(
                method = method(name),
                { result -> result.value == expected },
                invariants = arrayOf({ result -> result.value == expected }),
            )
        }
    }

    @Test
    fun `reduce preserves callback effects optional initial values and thrown values`() {
        val expectedValues = mapOf(
            "reduceMutation" to 75.0,
            "reduceOmitted" to 6.0,
            "reduceUndefined" to 8.0,
            "reduceInitialEmpty" to 12.0,
            "reduceThrows" to 7.0,
        )

        for ((name, expected) in expectedValues) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name),
                { result -> result.number == expected },
                invariants = arrayOf({ result -> result.number == expected }),
            )
        }
    }

    @Test
    fun `join keeps symbolic boolean nullish kinds and source selected lengths`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method("symbolicBooleans"),
            { first, second, both, result ->
                first.value && !second.value && both.value && result.value == "true-false"
            },
            { first, _, both, result -> !first.value && !both.value && result.value == "false" },
            invariants = arrayOf({ first, second, both, result ->
                val expected = if (both.value) "${first.value}-${second.value}" else first.value.toString()

                result.value == expected
            }),
        )

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method("symbolicNullish"),
            { flag, result -> flag.value && result.value == "true|" },
            { flag, result -> !flag.value && result.value == "|false" },
            invariants = arrayOf({ flag, result -> result.value == if (flag.value) "true|" else "|false" }),
        )
    }

    @Test
    fun `join formats safe integer limits special values and concrete decimal boundaries`() {
        val expectedValues = mapOf(
            "numericJoinSafeIntegerEdges" to "-9007199254740991|-1000000001|-10|-1|0|0|10|9007199254740991",
            "numericJoinSpecials" to "NaN|Infinity|-Infinity|0|0",
            "numericJoinConcreteDecimals" to "0.1|1e-7|0.000001|100000000000000000000|1e+21|1000000000000000100",
        )

        for ((name, expected) in expectedValues) {
            discoverProperties<TsTestValue.TsString>(
                method = method(name),
                { result -> result.value == expected },
                invariants = arrayOf({ result -> result.value == expected }),
            )
        }
    }

    @Test
    fun `join preserves symbolic integer digits special values and mixed runtime kinds`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method("numericJoinSymbolicInteger"),
            { value, result ->
                value.number in -12.0..-10.0 && result.value == value.number.toInt().toString()
            },
            { value, result -> value.number == 0.0 && result.value == "0" },
            { value, result -> value.number in 1.0..9.0 && result.value == value.number.toInt().toString() },
            { value, result -> value.number in 10.0..12.0 && result.value == value.number.toInt().toString() },
            { _, result -> result.value == "outside" },
            invariants = arrayOf({ value, result ->
                val number = value.number
                val smallInteger = number.isFinite() && number % 1.0 == 0.0 && number in -12.0..12.0
                val expected = if (smallInteger) number.toInt().toString() else "outside"

                result.value == expected
            }),
        )
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method("numericJoinSymbolicSpecial"),
            { value, result -> value.number.isNaN() && result.value == "NaN" },
            { value, result -> value.number == Double.POSITIVE_INFINITY && result.value == "Infinity" },
            { value, result -> value.number == Double.NEGATIVE_INFINITY && result.value == "-Infinity" },
            invariants = arrayOf({ value, result ->
                val expected = when {
                    value.number.isNaN() -> "NaN"
                    value.number == Double.POSITIVE_INFINITY -> "Infinity"
                    value.number == Double.NEGATIVE_INFINITY -> "-Infinity"
                    else -> "finite"
                }

                result.value == expected
            }),
        )
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method("numericJoinRuntimeKind"),
            { flag, result -> flag.value && result.value == "-42" },
            { flag, result -> !flag.value && result.value == "false" },
            invariants = arrayOf({ flag, result -> result.value == if (flag.value) "-42" else "false" }),
        )
    }

    @Test
    fun `numeric join includes its dependencies under an explicit model selection`() {
        val method = method("numericJoinSafeIntegerEdges")
        val selectedOptions = tsOptions.copy(
            unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf("ts.array.join", "ts.string.split")),
        )

        val analysis = TsMachine(scene = scene, options = options, tsOptions = selectedOptions)
            .use { machine -> machine.analyzeWithOutcome(listOf(method)) }
        val values = analysis.states.map { state -> TsTestResolver().resolve(method, state).returnValue }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertEquals(
            listOf(TsTestValue.TsString(value = "-9007199254740991|-1000000001|-10|-1|0|0|10|9007199254740991")),
            values,
        )
    }

    @Test
    fun `join leaves symbolic fractional and unsafe integer formatting residual`() {
        for (name in listOf("numericJoinSymbolicFraction", "numericJoinSymbolicUnsafeInteger")) {
            val events = mutableListOf<TsUnknownCallEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onUnknownCall(event: TsUnknownCallEvent) {
                    events += event
                }
            }
            val method = method(name)

            val analysis = TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer)
                .use { machine -> machine.analyzeWithOutcome(listOf(method)) }
            val values = analysis.states.map { state -> TsTestResolver().resolve(method, state).returnValue }
            val numericResidual = events.any { event ->
                event.callee.name == "elementString" && event.outcome == TsUnknownCallOutcome.PATH_STOPPED
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason, name)
            assertTrue(values.isNotEmpty(), name)
            assertTrue(values.all { value -> value == TsTestValue.TsString(value = "outside") }, "$name: $values")
            assertTrue(numericResidual, "$name: $events")
        }
    }

    @Test
    fun `unrelated helpers and unsupported coercions remain residual`() {
        val residualCallees = mapOf(
            "unrelatedPrivateHelper" to "elementString",
            "joinCallable" to "join",
            "splitCallable" to "split",
            "splitLong" to "split",
            "reduceShrink" to "requireDense",
        )

        for ((name, callee) in residualCallees) {
            val events = mutableListOf<TsUnknownCallEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onUnknownCall(event: TsUnknownCallEvent) {
                    events += event
                }
            }

            val analysis = TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer)
                .use { machine -> machine.analyzeWithOutcome(listOf(method(name))) }

            assertTrue(analysis.states.isEmpty(), name)
            val expectedResidual = events.any { event ->
                event.callee.name == callee && event.outcome == TsUnknownCallOutcome.PATH_STOPPED
            }
            assertTrue(expectedResidual, "$name: $events")
        }
    }

    @Test
    fun `callback deletion cannot escape as a stale exception payload`() {
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }
        }

        val analysis = TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer)
            .use { machine -> machine.analyzeWithOutcome(listOf(method("reduceDeleteThrows"))) }

        assertTrue(analysis.states.isEmpty())
        assertTrue(limitations.any { it.reason == TsRuntimeFeatureLimitationReason.ARRAY_ELEMENT_DELETE })
    }

    @Test
    fun `generated model witnesses replay in JavaScript`() {
        val methodNames = listOf(
            "splitEdges", "reduceAliases", "reduceEmptyError", "ignoredArguments", "composedMutations",
            "independentArrays", "joinRuntimeKinds", "joinNumbers", "reduceMutation", "reduceOmitted",
            "reduceUndefined",
            "reduceInitialEmpty", "reduceThrows", "symbolicBooleans", "symbolicNullish",
            "numericJoinSafeIntegerEdges", "numericJoinSpecials", "numericJoinConcreteDecimals",
            "numericJoinSymbolicInteger", "numericJoinSymbolicSpecial", "numericJoinRuntimeKind",
        )
        val script = buildString {
            appendLine(source.readText())
            for (name in methodNames) {
                for (test in runner(method(name), options)) {
                    val arguments = test.before.parameters.joinToString { jsValue(it) }
                    val expected = jsValue(test.returnValue)

                    appendLine(
                        "if (!Object.is(new GapModelSource().$name($arguments), $expected)) throw Error('$name');"
                    )
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "gap-model-source-witnesses",
            timeoutMessage = "Sequence and callback model replay timed out",
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "GapModelSource")

    private fun jsValue(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsBoolean -> value.value.toString()
        else -> assertIs<TsTestValue.TsNumber>(value).number.toString()
    }
}
