package org.usvm.machine.call

import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
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

class TsArrayPopEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayPopEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `empty array pop returns undefined through TypeScript model`() {
        val result = analyze(methodName = "emptyArray")

        assertIs<TsTestValue.TsUndefined>(result.values.single())
        assertEquals(listOf("ts.array.pop"), result.modelIds)
        assertTrue(assertNotNull(result.catalogFingerprint).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `non empty array pop executes source body and updates real array length`() {
        val result = analyze(methodName = "nonEmptyArray")

        assertEquals(32.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf(TsUnknownCallOutcome.MODEL_APPLIED), result.events.map { it.outcome })
    }

    @Test
    fun `symbolic number array uses the source model`() {
        val result = analyze(methodName = "symbolicNumberArray")

        assertTrue(result.values.isNotEmpty())
        assertEquals(listOf("ts.array.pop"), result.modelIds.distinct())
    }

    @Test
    fun `arrays outside the source model domain use fallback`() {
        assertUsesResidualFallback(methodName = "referenceArray")
        assertUsesResidualFallback(methodName = "symbolicUnknownArray")
    }

    @Test
    fun `unknown receiver does not prove an Array pop call`() {
        val result = analyze(methodName = "unknownReceiver")

        assertTrue(result.modelIds.isEmpty())
        assertEquals(listOf(TsUnknownCallOutcome.PATH_STOPPED), result.events.map { it.outcome })
    }

    @Test
    fun `fake wrapper receiver is outside the Array pop model domain`() {
        val state = analyzeStates(methodName = "unknownValue").single()
        val fakeReceiver = makeFakeReceiver(state)
        val models = TsBuiltInUnknownCallModels.catalog(
            enabledModelIds = setOf(TsBuiltInUnknownCallModels.ARRAY_POP_MODEL_ID),
        )

        val application = models.apply(state, arrayPopCall(fakeReceiver))

        assertIs<TsUnknownCallModelApplication.NotApplicable>(application)
    }

    @Test
    fun `arity mismatch uses fallback`() {
        assertUsesResidualFallback(methodName = "popWithArguments")
    }

    @Test
    fun `disabled pop model uses configured fallback`() {
        val result = analyze(
            methodName = "nonEmptyArray",
            tsOptions = TsOptions(
                enabledUnknownCallModelIds = setOf(TsBuiltInUnknownCallModels.ARRAY_SHIFT_MODEL_ID),
                unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            ),
        )

        assertEquals(listOf(TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN), result.events.map { it.outcome })
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
        val result = analyze(methodName)

        assertTrue(
            result.values.isEmpty(),
            "Expected fallback to stop the path, got values=${result.values}, events=${result.events}",
        )
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    private fun makeFakeReceiver(state: TsState): UConcreteHeapRef {
        val result = assertIs<TsMethodResult.Success>(state.methodResult).value
        val fakeReceiver = assertIs<UConcreteHeapRef>(result)

        assertTrue(with(state.ctx) { fakeReceiver.isFakeObject() })
        return fakeReceiver
    }

    private fun arrayPopCall(resolvedReceiver: UExpr<*>): TsUnknownCall {
        val callSite = method("nonEmptyArray").cfg.stmts.single { stmt ->
            stmt.callExpr?.callee?.name == "pop"
        }
        val sourceCall = assertIs<EtsInstanceCallExpr>(assertNotNull(callSite.callExpr))

        return TsUnknownCall(
            callee = sourceCall.callee,
            receiver = TsUnknownCallValue(source = sourceCall.instance, resolved = resolvedReceiver),
            arguments = emptyList(),
            resultType = sourceCall.type,
            callSite = callSite,
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        )
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
        .single { it.name == "ArrayPopEtsIr" }
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
