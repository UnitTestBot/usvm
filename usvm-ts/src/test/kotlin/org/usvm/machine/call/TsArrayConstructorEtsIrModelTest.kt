package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
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

class TsArrayConstructorEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayConstructorEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))
    private val importedShadowScene = loadEtsProjectAutoConvert(
        getResourcePath("/models/array-constructor-shadow"),
        useArkAnalyzerTypeInference = null,
    )

    @Test
    fun `callable Array creates holes and accepts in bounds writes`() {
        val result = analyze(methodName = "callableArrayCreatesHolesAndAcceptsWrites")

        assertEquals(13_456.0, result.singleNumber())
        assertEquals(
            listOf("ts.array.fromLength", "ts.array.primitive.allocate"),
            result.modelIds,
        )
    }

    @Test
    fun `callable Array result can be assigned to a nested array type`() {
        val result = analyze(methodName = "callableArraySupportsNestedAssignment")

        assertEquals(256.0, result.singleNumber())
        assertEquals(
            listOf("ts.array.fromLength", "ts.array.primitive.allocate"),
            result.modelIds,
        )
    }

    @Test
    fun `invalid callable Array lengths remain residual`() {
        for (methodName in listOf("negativeLength", "fractionalLength", "nanLength", "infiniteLength")) {
            val result = analyze(methodName = methodName)

            assertTrue(result.values.isEmpty(), methodName)
            assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome, methodName)
        }
    }

    @Test
    fun `valid length above model capacity remains residual`() {
        val result = analyze(methodName = "oversizedLength")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
    }

    @Test
    fun `full fill initializes every slot of a sparse array`() {
        val expectedResults = mapOf(
            "fullFillInitializesSparseNumberArray" to 3_777.0,
            "fullFillInitializesSparseBooleanArray" to 3_111.0,
        )

        for ((methodName, expected) in expectedResults) {
            val result = analyze(methodName = methodName)

            assertEquals(expected, result.singleNumber(), methodName)
            assertEquals(listOf("ts.array.fill", "ts.math.floor"), result.modelIds, methodName)
        }
    }

    @Test
    fun `shadowed Array function is not modeled as the global constructor`() {
        val result = analyze(methodName = "shadowedArrayIsNotModeled")

        assertEquals(2.0, result.singleNumber())
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `imported Array function is not modeled as the global constructor`() {
        val result = analyze(
            methodName = "callImportedArray",
            className = "ImportedArrayShadow",
            scene = importedShadowScene,
        )

        assertEquals(99.0, result.singleNumber())
        assertTrue(result.events.isEmpty())
    }

    private fun analyze(
        methodName: String,
        className: String = "ArrayConstructorEtsIr",
        scene: EtsScene = this.scene,
    ): AnalysisResult {
        val method = method(scene, className, methodName)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            val states = machine.analyze(listOf(method))
            AnalysisResult(
                values = states.map { state -> TsTestResolver().resolve(method, state).returnValue },
                events = observer.events.toList(),
            )
        }
    }

    private fun method(scene: EtsScene, className: String, methodName: String): EtsMethod = scene.projectClasses
        .single { it.name == className }
        .methods
        .single { it.name == methodName }

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
        fun singleNumber(): Double = assertIs<TsTestValue.TsNumber>(values.single()).number

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
