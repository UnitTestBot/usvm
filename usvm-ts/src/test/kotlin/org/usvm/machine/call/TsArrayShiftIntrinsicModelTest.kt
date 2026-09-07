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
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.intrinsic.TsArrayShiftIntrinsicModel
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

class TsArrayShiftIntrinsicModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayShiftIntrinsic.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `empty array shift returns undefined through intrinsic model`() {
        val result = analyze(methodName = "emptyArray")

        assertIs<TsTestValue.TsUndefined>(result.values.single())
        assertEquals(listOf("ts.array.shift"), result.modelIds)
        assertTrue(assertNotNull(result.catalogFingerprint).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `non empty array shift returns first element moves tail and shrinks array`() {
        val result = analyze(methodName = "nonEmptyArray")

        assertEquals(32.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf(TsUnknownCallOutcome.MODEL_APPLIED), result.events.map { it.outcome })
    }

    @Test
    fun `reference array preserves removed element alias`() {
        val result = analyze(methodName = "aliasedElement")

        assertEquals(42.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
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
    fun `array shift with arguments uses residual fallback`() {
        assertUsesResidualFallback(methodName = "shiftWithArguments")
    }

    @Test
    fun `fake wrapper receiver is not accepted as an array`() {
        val state = analyzeStates(methodName = "unknownValue").single()
        val fakeReceiver = makeFakeReceiver(state)

        val execution = TsArrayShiftIntrinsicModel.apply(state, arrayShiftCall(fakeReceiver))

        assertNull(execution)
    }

    @Test
    fun `conditional receiver containing fake wrapper is not accepted as an array`() {
        val state = analyzeStates(methodName = "unknownValue").single()
        val fakeReceiver = makeFakeReceiver(state)
        val fakeType = with(state.ctx) { fakeReceiver.getFakeType(state.memory) }
        val conditionalReceiver = state.ctx.mkIte(
            condition = fakeType.boolTypeExpr,
            trueBranch = fakeReceiver,
            falseBranch = state.makeSymbolicRefUntyped(),
        )

        val execution = TsArrayShiftIntrinsicModel.apply(state, arrayShiftCall(conditionalReceiver))

        assertNull(execution)
    }

    @Test
    fun `empty enabled set sends shift to configured fallback`() {
        val disabledResult = analyze(
            methodName = "nonEmptyArray",
            tsOptions = TsOptions(
                enabledUnknownCallModelIds = emptySet(),
                unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            ),
        )

        assertEquals(listOf(TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN), disabledResult.events.map { it.outcome })
    }

    @Test
    fun `compatibility dispatcher keeps the legacy shift approximation`() {
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

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.single().outcome)
    }

    private fun makeFakeReceiver(state: TsState): UConcreteHeapRef {
        val result = assertIs<TsMethodResult.Success>(state.methodResult).value
        val fakeReceiver = assertIs<UConcreteHeapRef>(result)

        assertTrue(with(state.ctx) { fakeReceiver.isFakeObject() })
        return fakeReceiver
    }

    private fun arrayShiftCall(resolvedReceiver: UExpr<*>): TsUnknownCall {
        val callSite = method("nonEmptyArray").cfg.stmts.single { stmt ->
            stmt.callExpr?.callee?.name == "shift"
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
        .single { it.name == "ArrayShiftIntrinsic" }
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
