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
import org.usvm.machine.call.intrinsic.TsErrorEtsIrModelFamily
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsErrorEtsIrModelTest {
    private val builtInSourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ErrorEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val shadowSourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/UserDefinedErrorEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val builtInScene = EtsScene(projectFiles = listOf(builtInSourceFile))
    private val shadowScene = EtsScene(projectFiles = listOf(shadowSourceFile))

    @Test
    fun `Error constructor initializes name and exact message through source model`() {
        val expected = mapOf(
            "name" to "Error",
            "message" to "expected message",
            "overwrittenName" to "CustomError",
            "overwrittenMessage" to "after",
            "anyErrorMessage" to "aliased message",
        )

        expected.forEach { (methodName, expectedValue) ->
            val result = analyze(scene = builtInScene, className = "ErrorEtsIr", methodName = methodName)
            val actual = assertIs<TsTestValue.TsString>(result.values.single()).value

            assertEquals(expectedValue, actual, methodName)
            assertEquals(listOf(TsErrorEtsIrModelFamily.CONSTRUCTOR_ID), result.modelIds)
        }
    }

    @Test
    fun `modeled Error receiver remains the thrown exception`() {
        val result = analyze(scene = builtInScene, className = "ErrorEtsIr", methodName = "throwError")

        assertEquals(1, result.states.size)
        assertIs<TsMethodResult.TsException>(result.states.single().methodResult)
        assertEquals(listOf(TsErrorEtsIrModelFamily.CONSTRUCTOR_ID), result.modelIds)
        assertTrue(result.values.single() is TsTestValue.TsException)
    }

    @Test
    fun `Error model storage does not affect ordinary any field resolution`() {
        listOf("anyForeignNameComparison", "anyForeignMessageComparison").forEach { methodName ->
            val result = analyze(scene = builtInScene, className = "ErrorEtsIr", methodName = methodName)
            val actual = result.values.map { value -> assertIs<TsTestValue.TsNumber>(value).number }

            assertEquals(listOf(1.0), actual, methodName)
            assertTrue(result.modelIds.isEmpty(), methodName)
        }
    }

    @Test
    fun `callback Error message remains residual`() {
        val result = analyze(scene = builtInScene, className = "ErrorEtsIr", methodName = "callbackMessage")

        assertTrue(result.states.isEmpty())
        assertEquals(
            listOf(TsResidualCallPolicy.STOP_PATH),
            result.events.mapNotNull { event ->
                (event.decision as? TsUnknownCallDecision.ResidualFallback)?.policy
            },
        )
        assertTrue(result.modelIds.isEmpty())
    }

    @Test
    fun `user defined Error constructor does not use builtin model`() {
        val result = analyze(scene = shadowScene, className = "UserDefinedErrorEtsIr", methodName = "name")
        val actual = assertIs<TsTestValue.TsString>(result.values.single()).value

        assertEquals("ShadowError", actual)
        assertTrue(result.modelIds.isEmpty())
    }

    private fun analyze(scene: EtsScene, className: String, methodName: String): AnalysisResult {
        val method = method(scene = scene, className = className, methodName = methodName)
        val observer = RecordingUnknownCallObserver()
        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }
        val values = states.map { state -> TsTestResolver().resolve(method, state).returnValue }

        return AnalysisResult(
            states = states,
            values = values,
            events = observer.events.toList(),
        )
    }

    private fun method(scene: EtsScene, className: String, methodName: String): EtsMethod = scene.projectClasses
        .single { clazz -> clazz.name == className }
        .methods
        .single { method -> method.name == methodName }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val states: List<org.usvm.machine.state.TsState>,
        val values: List<TsTestValue>,
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
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
