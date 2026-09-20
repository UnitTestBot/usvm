package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsSequenceEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/SequenceEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `array indexOf uses strict equality and offsets`() {
        val result = analyze(methodName = "arrayIndexOfUsesStrictEqualityAndOffsets")

        assertEquals(-681.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf("ts.array.indexOf", "ts.math.floor"), result.modelIds.distinct())
    }

    @Test
    fun `array includes uses SameValueZero`() {
        val result = analyze(methodName = "arrayIncludesUsesSameValueZero")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
        assertEquals(listOf("ts.array.includes", "ts.math.floor"), result.modelIds.distinct())
    }

    @Test
    fun `typed default searches fall back without array presence metadata`() {
        val result = analyze(methodName = "numericDefaultSearchFallsBack")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `array offsets normalize fractions and NaN`() {
        val result = analyze(methodName = "arrayOffsetsAreNormalized")

        assertEquals(131.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(setOf("ts.array.includes", "ts.array.indexOf", "ts.math.floor"), result.modelIds.toSet())
    }

    @Test
    fun `empty arrays do not match`() {
        val result = analyze(methodName = "emptyArraysDoNotMatch")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
    }

    @Test
    fun `explicit undefined uses the default array offset`() {
        val result = analyze(methodName = "arrayExplicitUndefinedOffset")

        assertEquals(0.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `array lastIndexOf searches backward from normalized offsets`() {
        val result = analyze(methodName = "arrayLastIndexOfHandlesOffsets")

        assertEquals(199.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertTrue("ts.array.lastIndexOf" in result.modelIds)
    }

    @Test
    fun `array lastIndexOf distinguishes omitted and undefined offsets`() {
        val result = analyze(methodName = "arrayLastIndexOfExplicitUndefined")

        assertEquals(0.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `array lastIndexOf stops before indexes below negative length`() {
        val result = analyze(methodName = "arrayLastIndexOfBeforeStart")

        assertEquals(-1.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `array searches canonicalize negative zero results`() {
        val result = analyze(methodName = "arraySearchReturnsPositiveZero")

        assertEquals(3.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `numeric holes do not match zero`() {
        val result = analyze(methodName = "numericHoleDoesNotMatchZero")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `numeric holes reject includes undefined without presence metadata`() {
        val result = analyze(methodName = "numericHoleDoesNotIncludeUndefined")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `symbolic typed-default search uses residual fallback`() {
        val result = analyze(methodName = "numericHoleWithSymbolicSearch")

        assertTrue(result.values.isNotEmpty())
        assertTrue(result.values.all { value -> !assertIs<TsTestValue.TsBoolean>(value).value })
        assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
        assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    @Test
    fun `array includes finds explicit undefined`() {
        val result = analyze(methodName = "explicitUndefinedArrayIncludesUndefined")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
    }

    @Test
    fun `array indexOf rejects undefined search when slot presence is unavailable`() {
        val result = analyze(methodName = "explicitUndefinedArrayIndexOfUndefined")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `string charAt handles in-range and out-of-range indexes`() {
        val result = analyze(methodName = "stringCharAtHandlesBounds")

        assertEquals("b", assertIs<TsTestValue.TsString>(result.values.single()).value)
        assertTrue("ts.string.charAt" in result.modelIds)
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `selecting charAt also enables its primitives`() {
        val result = analyze(
            methodName = "stringCharAtHandlesBounds",
            tsOptions = TsOptions(
                unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf("ts.string.charAt")),
            ),
        )

        assertEquals("b", assertIs<TsTestValue.TsString>(result.values.single()).value)
        assertEquals(
            setOf(
                "ts.string.charAt",
                "ts.math.floor",
                "ts.string.primitive.codeUnitAt",
                "ts.string.primitive.fromCodeUnit",
                "ts.string.primitive.length",
            ),
            result.modelIds.toSet(),
        )
    }

    @Test
    fun `string indexOf handles offsets and empty search`() {
        val result = analyze(methodName = "stringIndexOfHandlesOffsetsAndEmptySearch")

        assertEquals(330.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertTrue("ts.string.indexOf" in result.modelIds)
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `string includes handles NaN and infinity positions`() {
        val result = analyze(methodName = "stringIncludesHandlesNaNPosition")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
        assertTrue("ts.string.includes" in result.modelIds)
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `explicit undefined uses default string positions`() {
        val result = analyze(methodName = "stringExplicitUndefinedPositions")

        assertEquals(1.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `symbolic string position explores exact matches`() {
        val result = analyze(methodName = "stringSymbolicPosition")
        val numbers = result.values.filterIsInstance<TsTestValue.TsNumber>().map { it.number }.toSet()

        assertTrue(1.0 in numbers, "Expected first match for positions at or before 1: $numbers")
        assertTrue(3.0 in numbers, "Expected second match for positions 2 or 3: $numbers")
        assertTrue(-1.0 in numbers, "Expected no match after the last occurrence: $numbers")
        assertTrue("ts.string.indexOf" in result.modelIds)
    }

    @Test
    fun `symbolic charAt falls back until string value equality is modeled`() {
        val result = analyze(methodName = "symbolicCharAt")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `string charCodeAt returns code units and NaN out of bounds`() {
        val result = analyze(methodName = "stringCharCodeAtHandlesBounds")

        assertEquals(91.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertTrue("ts.string.charCodeAt" in result.modelIds)
    }

    @Test
    fun `string startsWith and endsWith honor positions`() {
        val result = analyze(methodName = "stringStartsAndEndsWithHandlePositions")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
        assertTrue(setOf("ts.string.startsWith", "ts.string.endsWith").all(result.modelIds::contains))
    }

    @Test
    fun `string lastIndexOf searches backward and matches empty suffix`() {
        val result = analyze(methodName = "stringLastIndexOfHandlesPositions")

        assertEquals(315.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertTrue("ts.string.lastIndexOf" in result.modelIds)
    }

    private fun analyze(
        methodName: String,
        tsOptions: TsOptions = TsOptions(),
    ): AnalysisResult {
        val method = method(methodName)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            observer = observer,
        ).use { machine ->
            val states = machine.analyze(listOf(method))
            val values = states.map { state -> TsTestResolver().resolve(method, state).returnValue }

            AnalysisResult(
                values = values,
                events = observer.events.toList(),
            )
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "SequenceEtsIr" }
        .methods
        .single { it.name == name }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val values: List<TsTestValue>,
        val events: List<TsUnknownCallEvent>,
    ) {
        val modelIds: List<String>
            get() = events.mapNotNull { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId
            }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 20_000L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
