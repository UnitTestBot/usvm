package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayPopIntrinsicModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayPopIntrinsic.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `empty array pop returns undefined through intrinsic model`() {
        val result = analyze(methodName = "emptyArray")

        assertIs<TsTestValue.TsUndefined>(result.values.single())
        assertEquals(listOf("ts.array.pop"), result.modelIds)
        assertTrue(assertNotNull(result.catalogFingerprint).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `non empty array pop returns last element and shrinks array`() {
        val result = analyze(methodName = "nonEmptyArray")

        assertEquals(32.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf(TsUnknownCallOutcome.MODEL_APPLIED), result.events.map { it.outcome })
    }

    @Test
    fun `allocated reference array uses residual fallback`() {
        assertUsesResidualFallback(methodName = "aliasedElement")
    }

    @Test
    fun `symbolic reference array uses residual fallback`() {
        assertUsesResidualFallback(methodName = "symbolicReferenceArray")
    }

    @Test
    fun `symbolic primitive array remains in the supported domain`() {
        val result = analyze(methodName = "symbolicNumberArray")

        assertTrue(result.values.isNotEmpty())
        assertEquals(listOf(TsUnknownCallOutcome.MODEL_APPLIED), result.events.map { it.outcome })
    }

    @Test
    fun `symbolic unknown array uses residual fallback`() {
        assertUsesResidualFallback(methodName = "symbolicUnknownArray")
    }

    @Test
    fun `allocated reference array with symbolic write uses residual fallback`() {
        val result = analyze(methodName = "allocatedReferenceArrayWithSymbolicWrite")

        val event = result.events.single()
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, event.outcome)
        assertIs<TsUnknownCallDecision.ResidualFallback>(event.decision)
    }

    @Test
    fun `array pop with arguments uses residual fallback`() {
        assertUsesResidualFallback(methodName = "popWithArguments")
    }

    @Disabled("Tracked by https://github.com/UnitTestBot/usvm/issues/379")
    @Test
    fun `symbolic reference array pop preserves fake value representations`() {
        val states = analyzeStates(methodName = "symbolicReferenceArrayPreservesFakeValue")

        assertTrue(
            states.any { state ->
                val result = (state.methodResult as? TsMethodResult.Success)?.value
                result == state.ctx.mkFp(44.0, state.ctx.fp64Sort)
            },
            "Expected the number representation to reach return 44",
        )
    }

    @Test
    fun `disabled model sends pop to configured residual fallback`() {
        val enabledModelIds = mutableSetOf("ts.array.pop")
        val selection = TsUnknownCallModelSelection(enabledModelIds = enabledModelIds)
        enabledModelIds.clear()
        val result = analyze(
            methodName = "nonEmptyArray",
            tsOptions = TsOptions(
                unknownCallProfile = TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL,
                unknownCallModels = TsUnknownCallModelSelection(enabledModelIds = emptySet()),
            ),
        )
        val selectedResult = analyze(
            methodName = "nonEmptyArray",
            tsOptions = TsOptions(unknownCallModels = selection),
        )

        assertEquals(listOf(TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN), result.events.map { it.outcome })
        assertIs<TsUnknownCallDecision.ResidualFallback>(result.events.single().decision)
        assertEquals(listOf("ts.array.pop"), selectedResult.modelIds)
    }

    @Test
    fun `compatibility dispatcher keeps the legacy pop approximation`() {
        val result = analyze(
            methodName = "nonEmptyArray",
            dispatcher = TsCompatibilityUnknownCallDispatcher,
        )

        assertEquals(32.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertTrue(result.events.isEmpty())
        assertNull(result.catalogFingerprint)
    }

    private fun analyze(
        methodName: String,
        tsOptions: TsOptions = TsOptions(),
        dispatcher: TsUnknownCallDispatcher? = null,
    ): AnalysisResult {
        val method = method(methodName)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            observer = observer,
            unknownCallDispatcher = dispatcher,
        ).use { machine ->
            val states = machine.analyze(listOf(method))
            val values = states.map { state -> TsTestResolver().resolve(method, state).returnValue }

            AnalysisResult(
                values = values,
                events = observer.events.toList(),
                catalogFingerprint = machine.unknownCallModelCatalogFingerprint,
            )
        }
    }

    private fun assertUsesResidualFallback(methodName: String) {
        val result = analyze(methodName = methodName)

        assertTrue(result.values.isEmpty())
        val event = result.events.single()
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, event.outcome)
        assertIs<TsUnknownCallDecision.ResidualFallback>(event.decision)
    }

    private fun analyzeStates(methodName: String): List<TsState> {
        val method = method(methodName)

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(listOf(method))
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "ArrayPopIntrinsic" }
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
        val catalogFingerprint: String?,
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
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
