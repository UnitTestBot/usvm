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
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallOutcome
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SparseArrayIteratorSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/SparseArrayIteratorSource.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 6)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
        val modelIds = mutableListOf<String>()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }

            override fun onUnknownCall(event: TsUnknownCallEvent) {
                val model = event.decision as? TsUnknownCallDecision.ModelApplied
                if (model != null) modelIds += model.modelId
            }
        }

        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer).use { machine ->
            val outcome = machine.analyzeWithOutcome(methods = listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, method.name)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "${method.name}: ${outcome.unsupportedPaths}")
            assertTrue(limitations.isEmpty(), "${method.name}: $limitations")
            val expectedModels = when (method.name) {
                "movesPreserveHoles", "truncateAndMoveHoles" -> setOf("ts.array.reverse", "ts.array.unshift")
                "reverseAsymmetricHoles", "reverseBoxedValues", "symbolicDeleteThenReverse" -> {
                    setOf("ts.array.reverse")
                }

                "unshiftHoles", "unshiftSeveralHoles", "unshiftNoItems" -> setOf("ts.array.unshift")
                else -> emptySet()
            }
            assertTrue(modelIds.containsAll(expectedModels), "${method.name}: $modelIds")
            if (expectedModels.isNotEmpty()) {
                assertTrue("ts.array.primitive.prepareOwnSlots" in modelIds, "${method.name}: $modelIds")
                assertTrue("ts.array.primitive.hasOwnSlot" in modelIds, "${method.name}: $modelIds")
            }
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
    fun `known own holes yield undefined through direct reads spread destructuring and live iteration`() {
        for (name in supportedMethods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `conditional holes reverse through the source model`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicDeleteThenReverse"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            invariants = arrayOf({ index, result ->
                val expected = when (index.number) {
                    0.0 -> 1.0
                    1.0 -> 2.0
                    else -> 0.0
                }
                result.number == expected
            }),
        )
    }

    @Test
    fun `symbolic deletion preserves conditional slot presence`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicDelete"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            { _, result -> result.number == 2.0 },
            invariants = arrayOf({ index, result ->
                val expected = when (index.number) {
                    0.0 -> 1.0
                    1.0 -> 2.0
                    else -> 0.0
                }
                result.number == expected
            }),
        )
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicWriteThenDelete"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ index, result ->
                val expected = if (index.number == 0.0 || index.number == 1.0) 1.0 else 0.0
                result.number == expected
            }),
        )
    }

    @Test
    fun `unmodeled prototype access and mutation cannot justify missing own slots`() {
        val unsupportedMethods = listOf(
            "mutateArrayPrototype", "mutateObjectPrototype", "mutatePrototypeThroughBracketAlias", "setArrayPrototype",
            "setArrayPrototypeThroughAliases", "getAndMutateArrayPrototype", "setLegacyPrototype",
            "getAndMutateObjectPrototype", "freezeThenDelete", "defineIndexedProperty",
        )
        val machineOptions = options.copy(throwExceptionOnStepFailure = false)

        for (name in unsupportedMethods) {
            TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
                val outcome = machine.analyzeWithOutcome(methods = listOf(method(name)))

                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, name)
                assertTrue(outcome.states.isEmpty(), name)
                assertTrue(outcome.unsupportedPaths.isNotEmpty(), "$name: $outcome")
            }
        }
    }

    @Test
    fun `unproven copy and fill storage stop at the source model boundary`() {
        for (name in listOf("unknownCopiedStorage", "unprovenPartialFillStorage")) {
            val limitations = mutableListOf<TsRuntimeFeatureLimitationReason>()
            val unknownCalls = mutableListOf<TsUnknownCallEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                    limitations += event.reason
                }

                override fun onUnknownCall(event: TsUnknownCallEvent) {
                    unknownCalls += event
                }
            }

            TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
                val outcome = machine.analyzeWithOutcome(methods = listOf(method(name)))

                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, name)
                assertTrue(outcome.states.isEmpty(), name)
                assertTrue(limitations.isEmpty(), "$name: $limitations")
                val event = unknownCalls.single()
                val expectedMethod = if (name == "unknownCopiedStorage") "slice" else "fill"

                assertEquals(expectedMethod, event.callee.name, name)
                assertEquals(TsUnknownCallFailureReason.PARTIAL_APPROXIMATION, event.failureReason, name)
                assertEquals(TsUnknownCallOutcome.PATH_STOPPED, event.outcome, name)
            }
        }
    }

    @Test
    fun `supported sparse array witnesses replay in native JavaScript`() {
        val script = buildString {
            appendLine(source.readText())
            for (name in supportedMethods) {
                appendLine("if (SparseArrayIteratorSource.$name() !== 1) throw Error('$name');")
            }
            for (name in listOf("symbolicDelete", "symbolicWriteThenDelete", "symbolicDeleteThenReverse")) {
                val tests = runner(method(name), options)
                assertTrue(tests.isNotEmpty(), name)
                for (test in tests) {
                    val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                    val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                    appendLine("if (SparseArrayIteratorSource.$name($input) !== $expected) throw Error('$name');")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "sparse-array-iterator",
            timeoutMessage = "Sparse array iterator replay timed out",
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "SparseArrayIteratorSource")

    private val supportedMethods = listOf(
        "literalHoles", "newArrayHoles", "deletedSlot", "rewriteDeletedSlot", "movesPreserveHoles",
        "shiftMissingFirstSlot", "truncateAndMoveHoles", "destructureHoles", "forOfHoles", "liveDelete",
        "sourceObjectLookalike", "reverseAsymmetricHoles", "unshiftHoles", "fillAllHoles",
        "unshiftSeveralHoles", "unshiftNoItems", "reverseBoxedValues",
    )
}
