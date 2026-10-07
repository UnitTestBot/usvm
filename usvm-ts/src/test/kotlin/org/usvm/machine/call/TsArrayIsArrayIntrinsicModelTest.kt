package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
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
import org.usvm.machine.call.intrinsic.TsArrayIsArrayIntrinsicModel
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayIsArrayIntrinsicModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayIsArrayIntrinsicModel.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(projectFiles = listOf(sourceFile))

    @Test
    fun `scalars are not arrays`() {
        val result = analyze("scalarValuesAreNotArrays", "missingArgumentIsNotArray")

        assertTrue(result.values.values.all { values -> assertIs<TsTestValue.TsBoolean>(values.single()).value })
        assertEquals(
            List(size = 6) { TsArrayIsArrayIntrinsicModel.MODEL_ID },
            result.modelIds,
        )
    }

    @Test
    fun `ordinary arrays are arrays`() {
        val result = analyze("ordinaryArraysAreArrays")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.getValue("ordinaryArraysAreArrays").single()).value)
        assertEquals(
            List(size = 2) { TsArrayIsArrayIntrinsicModel.MODEL_ID },
            result.modelIds,
        )
    }

    @Test
    fun `null and undefined parameters are not arrays`() {
        val result = analyze("nullParameterIsNotArray", "undefinedParameterIsNotArray")

        result.values.values.forEach { values ->
            assertFalse(assertIs<TsTestValue.TsBoolean>(values.single()).value)
        }
        assertEquals(
            List(size = 2) { TsArrayIsArrayIntrinsicModel.MODEL_ID },
            result.modelIds,
        )
    }

    @Test
    fun `string and nested array parameters are arrays`() {
        val result = analyze("stringArrayIsArray", "nestedArrayIsArray")

        result.values.values.forEach { values ->
            assertTrue(assertIs<TsTestValue.TsBoolean>(values.single()).value)
        }
        assertEquals(
            List(size = 2) { TsArrayIsArrayIntrinsicModel.MODEL_ID },
            result.modelIds,
        )
    }

    @Test
    fun `non builtin isArray call is not claimed by the model`() {
        val result = analyze("nonBuiltinIsArrayFallsBack")

        assertTrue(result.modelIds.isEmpty())
        assertTrue(result.events.none { event -> event.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    private fun analyze(vararg methodNames: String): AnalysisResult {
        val methods = methodNames.associateWith(::method)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(unknownCallFallback = TsResidualCallPolicy.STOP_PATH),
            observer = observer,
        ).use { machine ->
            val states = machine.analyze(methods.values.toList())
            val values = methods.mapValues { (_, method) ->
                states.filter { state -> state.entrypoint === method }
                    .map { state -> TsTestResolver().resolve(method, state).returnValue }
            }

            AnalysisResult(
                values = values,
                events = observer.events.toList(),
            )
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { clazz -> clazz.name == DEFAULT_ARK_CLASS_NAME && clazz.declaringFile === sourceFile }
        .methods
        .single { method -> method.name == name }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val values: Map<String, List<TsTestValue>>,
        val events: List<TsUnknownCallEvent>,
    ) {
        val modelIds: List<String> = events.mapNotNull { event ->
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
            stepsFromLastCovered = 1_000L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
