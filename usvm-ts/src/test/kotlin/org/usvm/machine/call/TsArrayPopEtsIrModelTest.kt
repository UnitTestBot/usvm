package org.usvm.machine.call

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
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
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
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
    fun `widened and wrapped receivers retain number array storage`() {
        for (methodName in listOf("widenedReceiver", "wrappedReceiver")) {
            val result = analyze(methodName = methodName)

            assertEquals(32.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number, methodName)
            assertEquals(listOf("ts.array.pop"), result.modelIds, methodName)
        }
    }

    @Test
    fun `model can be entered again after returning`() {
        val result = analyze(methodName = "sequentialPops")

        assertEquals(51.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf("ts.array.pop", "ts.array.pop"), result.modelIds)
    }

    @Test
    fun `length assignment through aliases updates the original array`() {
        for (methodName in listOf("shrinkThroughWidenedAlias", "shrinkThroughWrappedAlias")) {
            val result = analyze(methodName = methodName)

            assertEquals(11.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number, methodName)
        }
    }

    @Test
    fun `negative zero is a valid zero array length`() {
        val result = analyze(methodName = "negativeZeroLength")

        assertEquals(0.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
    }

    @Test
    fun `unsupported length value stops without repeating the assignment`() {
        var lengthAssignments = 0
        val observer = object : TsInterpreterObserver {
            override fun onAssignStatement(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsAssignStmt,
                scope: TsStepScope,
            ) {
                if ((stmt.lhv as? EtsInstanceFieldRef)?.field?.name == "length") {
                    lengthAssignments++
                }
            }
        }

        val states = TsMachine(
            scene = scene,
            options = machineOptions.copy(stepLimit = 100uL),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method("unsupportedLengthValue"))) }

        assertTrue(states.isEmpty())
        assertEquals(1, lengthAssignments)
    }

    @Test
    fun `array length growth after pop is unsupported`() {
        val result = analyze(methodName = "popThenGrow")

        assertTrue(result.values.isEmpty())
        assertEquals(listOf(TsUnknownCallOutcome.MODEL_APPLIED), result.events.map { it.outcome })
    }

    @Test
    fun `fresh array length growth is unsupported`() {
        val result = analyze(methodName = "growFreshArray")

        assertTrue(result.values.isEmpty())
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `symbolic number array uses the source model`() {
        val result = analyze(methodName = "symbolicNumberArray")

        assertTrue(result.values.isNotEmpty())
        assertEquals(listOf("ts.array.pop"), result.modelIds.distinct())
    }

    @Test
    fun `reference and unresolved arrays use the source model`() {
        for ((methodName, expected) in listOf("referenceArray" to 42.0, "symbolicUnknownArray" to 47.0)) {
            val result = analyze(methodName = methodName)

            assertTrue(result.values.filterIsInstance<TsTestValue.TsNumber>().any { it.number == expected }, methodName)
            assertEquals(listOf("ts.array.pop"), result.modelIds.distinct(), methodName)
            assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED }, methodName)
        }
    }

    @Test
    fun `unknown receiver does not prove an Array pop call`() {
        val result = analyze(methodName = "unknownReceiver")

        assertTrue(result.modelIds.isEmpty())
        assertTrue(result.events.isNotEmpty())
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    @Test
    fun `fake wrapper receiver is outside the Array pop model domain`() {
        val state = analyzeStates(methodName = "unknownValue").single()
        val fakeReceiver = makeFakeReceiver(state)
        val models = TsBuiltInUnknownCallModels.catalog(
            selection = TsUnknownCallModelSelection.Only(setOf("ts.array.pop")),
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
                unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf("ts.array.shift")),
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
